package com.keylerperales07.projectmountains.resimdos.runtime.asm

import java.io.ByteArrayOutputStream

/**
 * Lee el texto de un programa ASM y lo convierte en una [ProgramImage].
 *
 * Trabaja en dos pasadas, como un ensamblador de la época:
 *  1. Recorre las líneas, separa etiqueta / mnemónico / operandos, va
 *     contando posiciones y anota dónde cae cada etiqueta. Aquí solo se
 *     pueden evaluar ORG y EQU, porque exigen valores ya conocidos.
 *  2. Con todas las etiquetas ya anotadas, evalúa cada operando. Por eso
 *     un salto hacia adelante (JMP fin ... fin:) funciona.
 *
 * Convención de operandos (la de la especificación, no la de MASM): un
 * nombre suelto vale su dirección o su constante, y solo los corchetes
 * indican memoria. Es decir, MOV DX, mensaje carga la dirección de
 * mensaje, y MOV AL, [valor] lee el byte que hay allí.
 *
 * Una instancia sirve para un solo archivo; guarda el estado de esa lectura.
 */
internal class AsmParser {

    private class PendingCode(
        val line: Int,
        val address: Int,
        val op: Op,
        val condition: Condition?,
        val operands: List<RawOperand>
    )

    private sealed class DataElement {
        class Text(val chars: String) : DataElement()
        class Value(val expr: Expr) : DataElement()
        object Blank : DataElement()
    }

    private class PendingData(
        val line: Int,
        val address: Int,
        val isWord: Boolean,
        val elements: List<DataElement>
    )

    private val symbols = mutableMapOf<String, Int>()
    private val pendingCode = mutableListOf<PendingCode>()
    private val pendingData = mutableListOf<PendingData>()
    private var counter = COM_ORIGIN
    private var originDeclared = false

    fun parse(source: String): ProgramImage {
        val lines = source.split('\n')
        for ((index, rawLine) in lines.withIndex()) {
            val text = removeComment(rawLine.trimEnd('\r')).trim()
            if (text.isEmpty()) continue
            // La línea 1 solo anuncia el tipo; el cargador ya la leyó. Se salta SIN
            // quitarla del texto para que los números de línea de los errores
            // coincidan con los del archivo.
            if (index == 0 && text.replace(" ", "").uppercase() == "FILETYPE=ASM") continue
            readLine(text, index + 1)
        }
        return resolve()
    }

    // ---------------------------------------------------------------- primera pasada

    private fun readLine(text: String, line: Int) {
        var rest = text
        var label: String? = null

        val firstEnd = rest.indexOfFirst { it.isWhitespace() || it == ':' }
        val first = if (firstEnd < 0) rest else rest.substring(0, firstEnd)

        if (firstEnd >= 0 && rest[firstEnd] == ':') {
            label = first
            rest = rest.substring(firstEnd + 1).trim()
        } else if (!isKnownWord(first)) {
            // Etiqueta sin dos puntos (msg DB '...'): solo es válida si después viene
            // una instrucción o directiva; si no, la palabra es un mnemónico mal escrito.
            val afterFirst = rest.substring(first.length).trim()
            val second = afterFirst.takeWhile { !it.isWhitespace() }
            if (!isKnownWord(second)) throw AsmException("Unknown instruction", line)
            label = first
            rest = afterFirst
        }

        if (label != null) checkLabelName(label, line)

        if (rest.isEmpty()) {
            if (label != null) define(label, counter, line)
            return
        }

        val word = rest.takeWhile { !it.isWhitespace() }
        val mnemonic = word.uppercase()
        val operandText = rest.substring(word.length).trim()

        when (mnemonic) {
            "EQU" -> {
                if (label == null || operandText.isEmpty()) throw AsmException("Syntax error", line)
                define(label, evaluate(parseExpression(operandText, line), counter, line), line)
            }
            "ORG" -> {
                if (label != null || originDeclared || pendingCode.isNotEmpty() || pendingData.isNotEmpty()) {
                    throw AsmException("Syntax error", line)
                }
                val value = evaluate(parseExpression(operandText, line), counter, line)
                // Un .COM siempre carga en 100h: el cargador arranca con IP = 100h.
                if (value != COM_ORIGIN) throw AsmException("Invalid operand", line)
                originDeclared = true
            }
            "DB", "DW" -> {
                if (label != null) define(label, counter, line)
                readData(isWord = mnemonic == "DW", operandText = operandText, line = line)
            }
            else -> {
                if (label != null) define(label, counter, line)
                readInstruction(mnemonic, operandText, line)
            }
        }
    }

    private fun readInstruction(mnemonic: String, operandText: String, line: Int) {
        val op = OPS_BY_NAME[mnemonic]
        val condition = CONDITIONS_BY_NAME[mnemonic]
        if (op == null && condition == null) {
            // Distinguir "no existe" de "existe en el 8086 pero aún no toca" ayuda a
            // saber si el error es del programa o del avance del roadmap.
            if (mnemonic in PLANNED_WORDS) throw AsmException("Not supported yet", line)
            throw AsmException("Unknown instruction", line)
        }

        val operands = if (operandText.isEmpty()) {
            emptyList()
        } else {
            splitList(operandText, line).map { readOperand(it, line) }
        }
        pendingCode += PendingCode(line, counter, op ?: Op.JCC, condition, operands)
        advance(INSTRUCTION_SLOT_BYTES, line)
    }

    private fun readData(isWord: Boolean, operandText: String, line: Int) {
        if (operandText.isEmpty()) throw AsmException("Syntax error", line)

        val elements = splitList(operandText, line).map { piece ->
            if (piece == "?") return@map DataElement.Blank
            val quoted = readQuoted(piece)
            if (quoted != null && !isWord) {
                if (quoted.isEmpty() || quoted.any { it.code > 0xFF }) throw AsmException("Invalid operand", line)
                DataElement.Text(quoted)
            } else {
                DataElement.Value(parseExpression(piece, line))
            }
        }

        val size = elements.fold(0) { total, element ->
            total + when (element) {
                is DataElement.Text -> element.chars.length
                else -> if (isWord) 2 else 1
            }
        }
        pendingData += PendingData(line, counter, isWord, elements)
        advance(size, line)
    }

    private fun advance(bytes: Int, line: Int) {
        counter += bytes
        if (counter > MAX_END_OFFSET) throw AsmException("Program too large", line)
    }

    private fun define(name: String, value: Int, line: Int) {
        val key = name.uppercase()
        if (symbols.containsKey(key)) throw AsmException("Duplicate symbol", line)
        symbols[key] = value
    }

    private fun checkLabelName(label: String, line: Int) {
        val key = label.uppercase()
        if (!LABEL_PATTERN.matches(label) ||
            RegisterName.fromText(key) != null ||
            isKnownWord(key) ||
            key == "OFFSET" || key == "PTR" || key == "BYTE" || key == "WORD"
        ) {
            throw AsmException("Syntax error", line)
        }
    }

    private fun isKnownWord(word: String): Boolean {
        val key = word.uppercase()
        return key in DIRECTIVES || key in OPS_BY_NAME || key in CONDITIONS_BY_NAME || key in PLANNED_WORDS
    }

    // ---------------------------------------------------------------- segunda pasada

    private fun resolve(): ProgramImage {
        val instructions = HashMap<Int, Instruction>()
        for (item in pendingCode) instructions[item.address] = buildInstruction(item)
        val blocks = pendingData.map { buildData(it) }
        return ProgramImage(COM_ORIGIN, counter, blocks, instructions)
    }

    private fun evaluate(expr: Expr, here: Int, line: Int): Int = when (expr) {
        is Expr.Literal -> expr.value
        is Expr.Symbol -> symbols[expr.name] ?: throw AsmException("Undefined symbol", line)
        is Expr.Here -> here
        is Expr.Negate -> -evaluate(expr.inner, here, line)
        is Expr.Binary -> {
            val left = evaluate(expr.left, here, line)
            val right = evaluate(expr.right, here, line)
            when (expr.operator) {
                '+' -> left + right
                '-' -> left - right
                '*' -> left * right
                else -> {
                    if (right == 0) throw AsmException("Division by zero", line)
                    left / right
                }
            }
        }
    }

    private fun buildData(item: PendingData): DataBlock {
        val buffer = ByteArrayOutputStream()
        for (element in item.elements) {
            when (element) {
                is DataElement.Text -> element.chars.forEach { buffer.write(it.code) }
                is DataElement.Blank -> {
                    buffer.write(0)
                    if (item.isWord) buffer.write(0)
                }
                is DataElement.Value -> {
                    val value = evaluate(element.expr, item.address, item.line)
                    val allowed = if (item.isWord) -0x8000..0xFFFF else -0x80..0xFF
                    if (value !in allowed) throw AsmException("Invalid operand", item.line)
                    buffer.write(value and 0xFF)
                    if (item.isWord) buffer.write((value shr 8) and 0xFF)
                }
            }
        }
        return DataBlock(item.address, buffer.toByteArray())
    }

    private fun buildInstruction(item: PendingCode): Instruction {
        val operands = item.operands.map { raw ->
            when (raw) {
                is RawOperand.Register -> Operand.Register(raw.register)
                is RawOperand.Immediate -> Operand.Immediate(evaluate(raw.expr, item.address, item.line))
                is RawOperand.Memory -> Operand.Memory(
                    base = raw.base,
                    offset = raw.address?.let { evaluate(it, item.address, item.line) and 0xFFFF } ?: 0,
                    segment = raw.segment ?: RegisterName.DS,
                    width = raw.width
                )
            }
        }
        val width = checkShape(item, operands)
        return Instruction(item.line, item.address, item.op, item.condition, operands, width)
    }

    /**
     * Comprueba que los operandos sean una combinación que el 8086
     * acepta y devuelve el ancho de la operación (8 o 16). Falla ahora,
     * antes de ejecutar nada, para que un error de sintaxis no deje el
     * programa a medias en pantalla.
     */
    private fun checkShape(item: PendingCode, operands: List<Operand>): Int {
        val line = item.line
        fun invalid(): Nothing = throw AsmException("Invalid operand", line)

        return when (item.op) {
            Op.NOP -> {
                if (operands.isNotEmpty()) invalid()
                0
            }
            Op.INT -> {
                val number = (operands.singleOrNull() as? Operand.Immediate)?.value ?: invalid()
                if (number !in 0..0xFF) invalid()
                0
            }
            Op.JMP, Op.JCC -> {
                val target = (operands.singleOrNull() as? Operand.Immediate)?.value ?: invalid()
                if (target !in 0..0xFFFF) invalid()
                0
            }
            Op.INC, Op.DEC -> {
                val target = operands.singleOrNull() ?: invalid()
                if (target is Operand.Immediate) invalid()
                if (target is Operand.Register && target.register.group == RegisterGroup.SEGMENT) invalid()
                widthOf(target) ?: invalid()
            }
            Op.MOV, Op.ADD, Op.SUB, Op.CMP -> {
                if (operands.size != 2) invalid()
                val dest = operands[0]
                val source = operands[1]
                if (dest is Operand.Immediate) invalid()
                if (dest is Operand.Memory && source is Operand.Memory) invalid()

                val segmentInvolved = isSegmentRegister(dest) || isSegmentRegister(source)
                if (segmentInvolved) {
                    // Solo MOV segmento <-> registro de 16 bits; CS no se puede escribir.
                    if (item.op != Op.MOV) invalid()
                    if (dest !is Operand.Register || source !is Operand.Register) invalid()
                    if (dest.register.group == RegisterGroup.SEGMENT && source.register.group == RegisterGroup.SEGMENT) invalid()
                    if (dest.register.group == RegisterGroup.GENERAL8 || source.register.group == RegisterGroup.GENERAL8) invalid()
                    if (dest.register == RegisterName.CS) invalid()
                    return 16
                }

                val destWidth = widthOf(dest)
                val sourceWidth = widthOf(source)
                // Memoria con inmediato y sin BYTE/WORD PTR es ambiguo: el 8086 no puede adivinar el tamaño.
                val width = destWidth ?: sourceWidth ?: invalid()
                if (destWidth != null && sourceWidth != null && destWidth != sourceWidth) invalid()

                if (source is Operand.Immediate) {
                    val allowed = if (width == 8) -0x80..0xFF else -0x8000..0xFFFF
                    if (source.value !in allowed) invalid()
                }
                width
            }
        }
    }

    private fun widthOf(operand: Operand): Int? = when (operand) {
        is Operand.Register -> operand.register.width
        is Operand.Memory -> operand.width
        is Operand.Immediate -> null
    }

    private fun isSegmentRegister(operand: Operand): Boolean =
        operand is Operand.Register && operand.register.group == RegisterGroup.SEGMENT

    // ---------------------------------------------------------------- lectura de operandos

    private fun readOperand(text: String, line: Int): RawOperand {
        var body = text.trim()
        var width: Int? = null
        var segment: RegisterName? = null

        // SHORT y NEAR orientan al ensamblador en saltos reales; aquí no cambian nada.
        JUMP_HINT.find(body)?.let { body = body.substring(it.value.length).trim() }

        PTR_PREFIX.find(body)?.let {
            width = if (it.groupValues[1].equals("BYTE", ignoreCase = true)) 8 else 16
            body = body.substring(it.value.length).trim()
        }

        SEGMENT_PREFIX.find(body)?.let {
            segment = RegisterName.fromText(it.groupValues[1])
            body = body.substring(it.value.length).trim()
            if (!body.startsWith("[")) throw AsmException("Invalid operand", line)
        }

        if (body.startsWith("[")) {
            if (!body.endsWith("]")) throw AsmException("Syntax error", line)
            return readMemory(body.substring(1, body.length - 1).trim(), width, segment, line)
        }
        // BYTE/WORD PTR y los prefijos de segmento solo tienen sentido sobre memoria.
        if (width != null || segment != null) throw AsmException("Invalid operand", line)

        RegisterName.fromText(body)?.let { return RawOperand.Register(it) }
        return RawOperand.Immediate(parseExpression(body, line))
    }

    private fun readMemory(inner: String, width: Int?, segment: RegisterName?, line: Int): RawOperand {
        if (inner.isEmpty()) throw AsmException("Syntax error", line)

        val registersInside = NAME_TOKEN.findAll(inner)
            .map { it.value.uppercase() }
            .filter { RegisterName.fromText(it) != null }
            .toList()

        if (registersInside.isEmpty()) {
            return RawOperand.Memory(null, parseExpression(inner, line), width, segment)
        }

        // v0.4 solo admite [BX], [SI] y [DI]; sumas con desplazamiento o BP quedan fuera.
        val only = registersInside.singleOrNull()
        if (only == null || inner.uppercase() != only || only !in INDIRECT_BASES) {
            throw AsmException("Invalid operand", line)
        }
        return RawOperand.Memory(RegisterName.fromText(only), null, width, segment)
    }

    private fun parseExpression(text: String, line: Int): Expr = ExprReader(text, line).readAll()

    // ---------------------------------------------------------------- utilidades de texto

    /** Quita el comentario (desde ";") sin cortar un ";" que esté dentro de una cadena. */
    private fun removeComment(text: String): String {
        var quote: Char? = null
        for ((i, ch) in text.withIndex()) {
            if (quote != null) {
                if (ch == quote) quote = null
            } else if (ch == '\'' || ch == '"') {
                quote = ch
            } else if (ch == ';') {
                return text.substring(0, i)
            }
        }
        return text
    }

    /** Parte "a, b, c" en piezas, sin romper comas dentro de comillas, corchetes o paréntesis. */
    private fun splitList(text: String, line: Int): List<String> {
        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var depth = 0

        for (ch in text) {
            if (quote != null) {
                current.append(ch)
                if (ch == quote) quote = null
            } else if (ch == '\'' || ch == '"') {
                current.append(ch)
                quote = ch
            } else if (ch == '[' || ch == '(') {
                depth++
                current.append(ch)
            } else if (ch == ']' || ch == ')') {
                depth--
                current.append(ch)
            } else if (ch == ',' && depth == 0) {
                pieces += current.toString().trim()
                current.setLength(0)
            } else {
                current.append(ch)
            }
        }
        pieces += current.toString().trim()

        if (quote != null || depth != 0 || pieces.any { it.isEmpty() }) throw AsmException("Syntax error", line)
        return pieces
    }

    /** Si todo el texto es UNA cadena entre comillas ('Hola' o "Hola"), devuelve su contenido; si no, null. Una comilla doble dentro ('It''s') cuenta como una. */
    private fun readQuoted(text: String): String? {
        if (text.length < 2) return null
        val quote = text[0]
        if (quote != '\'' && quote != '"') return null

        val content = StringBuilder()
        var i = 1
        while (i < text.length) {
            val ch = text[i]
            if (ch == quote) {
                if (i + 1 < text.length && text[i + 1] == quote) {
                    content.append(quote)
                    i += 2
                    continue
                }
                return if (i == text.length - 1) content.toString() else null
            }
            content.append(ch)
            i++
        }
        return null
    }

    companion object {
        /** Dirección de carga de un .COM. */
        const val COM_ORIGIN = 0x100

        /** Hasta aquí puede crecer el programa; el resto del segmento queda para la pila (SP arranca en FFFEh). */
        const val MAX_END_OFFSET = 0xFF00

        private val DIRECTIVES = setOf("ORG", "DB", "DW", "EQU")

        private val OPS_BY_NAME = mapOf(
            "MOV" to Op.MOV, "ADD" to Op.ADD, "SUB" to Op.SUB, "CMP" to Op.CMP,
            "INC" to Op.INC, "DEC" to Op.DEC, "JMP" to Op.JMP, "NOP" to Op.NOP, "INT" to Op.INT
        )

        private val CONDITIONS_BY_NAME = mapOf(
            "JO" to Condition.O, "JNO" to Condition.NO,
            "JB" to Condition.B, "JNAE" to Condition.B, "JC" to Condition.B,
            "JAE" to Condition.AE, "JNB" to Condition.AE, "JNC" to Condition.AE,
            "JE" to Condition.E, "JZ" to Condition.E,
            "JNE" to Condition.NE, "JNZ" to Condition.NE,
            "JBE" to Condition.BE, "JNA" to Condition.BE,
            "JA" to Condition.A, "JNBE" to Condition.A,
            "JS" to Condition.S, "JNS" to Condition.NS,
            "JP" to Condition.P, "JPE" to Condition.P,
            "JNP" to Condition.NP, "JPO" to Condition.NP,
            "JL" to Condition.L, "JNGE" to Condition.L,
            "JGE" to Condition.GE, "JNL" to Condition.GE,
            "JLE" to Condition.LE, "JNG" to Condition.LE,
            "JG" to Condition.G, "JNLE" to Condition.G
        )

        /** Lo que la especificación v0.4 promete pero los pasos siguientes del roadmap todavía no implementan. */
        private val PLANNED_WORDS = (
            "XCHG LEA LDS LES XLAT PUSH POP PUSHF POPF LAHF SAHF IN OUT " +
            "ADC SBB NEG MUL IMUL DIV IDIV CBW CWD AAA AAS AAM AAD DAA DAS " +
            "AND OR XOR NOT TEST SHL SAL SHR SAR ROL ROR RCL RCR " +
            "MOVSB MOVSW CMPSB CMPSW SCASB SCASW LODSB LODSW STOSB STOSW " +
            "REP REPE REPZ REPNE REPNZ CALL RET RETF LOOP LOOPE LOOPZ LOOPNE LOOPNZ JCXZ " +
            "INTO IRET HLT WAIT LOCK ESC CLC STC CMC CLD STD CLI STI " +
            "DD DUP PROC ENDP SEGMENT ENDS ASSUME END INCLUDE MACRO ENDM"
        ).split(' ').toSet()

        private val INDIRECT_BASES = setOf("BX", "SI", "DI")

        private val LABEL_PATTERN = Regex("[A-Za-z_@][A-Za-z0-9_@]*")
        private val NAME_TOKEN = Regex("\\b[A-Za-z_@][A-Za-z0-9_@]*\\b")
        private val JUMP_HINT = Regex("^(SHORT|NEAR)\\s+", RegexOption.IGNORE_CASE)
        private val PTR_PREFIX = Regex("^(BYTE|WORD)\\s+PTR\\s+", RegexOption.IGNORE_CASE)
        private val SEGMENT_PREFIX = Regex("^(CS|DS|ES|SS)\\s*:", RegexOption.IGNORE_CASE)
    }
}

/**
 * Lee una expresión aritmética de operando: números (10, 0Ah, 'A'),
 * símbolos, $, OFFSET nombre, paréntesis y los operadores + - * /.
 * Los símbolos quedan sin valor (se resuelven en la segunda pasada).
 */
private class ExprReader(private val text: String, private val line: Int) {

    private var pos = 0

    fun readAll(): Expr {
        val result = readSum()
        skipSpaces()
        if (pos < text.length) throw AsmException("Syntax error", line)
        return result
    }

    private fun readSum(): Expr {
        var left = readProduct()
        while (true) {
            skipSpaces()
            val operator = peek() ?: break
            if (operator != '+' && operator != '-') break
            pos++
            left = Expr.Binary(operator, left, readProduct())
        }
        return left
    }

    private fun readProduct(): Expr {
        var left = readFactor()
        while (true) {
            skipSpaces()
            val operator = peek() ?: break
            if (operator != '*' && operator != '/') break
            pos++
            left = Expr.Binary(operator, left, readFactor())
        }
        return left
    }

    private fun readFactor(): Expr {
        skipSpaces()
        val c = peek() ?: throw AsmException("Syntax error", line)
        return when {
            c == '-' -> { pos++; Expr.Negate(readFactor()) }
            c == '+' -> { pos++; readFactor() }
            c == '(' -> {
                pos++
                val inner = readSum()
                skipSpaces()
                if (peek() != ')') throw AsmException("Syntax error", line)
                pos++
                inner
            }
            c == '$' -> { pos++; Expr.Here }
            c == '\'' || c == '"' -> readCharLiteral(c)
            c.isDigit() -> readNumber()
            c.isLetter() || c == '_' || c == '@' -> readName()
            else -> throw AsmException("Syntax error", line)
        }
    }

    private fun readNumber(): Expr {
        val word = readWord().uppercase()
        // Sufijo h = hexadecimal (0Ah, 100h); sin sufijo = decimal. El 0 inicial de 0Ah es parte del número.
        val value = if (word.endsWith("H")) word.dropLast(1).toIntOrNull(16) else word.toIntOrNull()
        return Expr.Literal(value ?: throw AsmException("Syntax error", line))
    }

    private fun readName(): Expr {
        val upper = readWord().uppercase()
        if (upper == "OFFSET") {
            skipSpaces()
            val target = readWord().uppercase()
            if (target.isEmpty() || target[0].isDigit() || RegisterName.fromText(target) != null) {
                throw AsmException("Invalid operand", line)
            }
            return Expr.Symbol(target)
        }
        // Un registro dentro de una suma (BX+1) no es una expresión de constantes.
        if (RegisterName.fromText(upper) != null) throw AsmException("Invalid operand", line)
        return Expr.Symbol(upper)
    }

    private fun readCharLiteral(quote: Char): Expr {
        pos++
        val content = StringBuilder()
        while (true) {
            if (pos >= text.length) throw AsmException("Syntax error", line)
            val ch = text[pos++]
            if (ch != quote) {
                content.append(ch)
            } else if (pos < text.length && text[pos] == quote) {
                content.append(quote)
                pos++
            } else {
                break
            }
        }
        // Como número solo cabe un carácter; las cadenas largas son cosa de DB.
        if (content.length != 1 || content[0].code > 0xFF) throw AsmException("Invalid operand", line)
        return Expr.Literal(content[0].code)
    }

    private fun readWord(): String {
        val start = pos
        while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_' || text[pos] == '@')) pos++
        return text.substring(start, pos)
    }

    private fun peek(): Char? = if (pos < text.length) text[pos] else null

    private fun skipSpaces() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }
}
