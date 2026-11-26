package com.keylerperales07.projectmountains.resimdos.runtime.asm

/**
 * Cuánto ocupa cada instrucción en el contador de posiciones.
 *
 * Mientras el motor ejecute el texto directamente no existe la
 * codificación a bytes, así que el tamaño real de cada instrucción no
 * se conoce. Todas ocupan una "casilla" fija; lo único que importa es
 * que el código y los datos no se pisen y que las etiquetas tengan
 * direcciones coherentes. Cuando exista OpcodeTable (Paso 2 en
 * adelante) este valor se reemplaza por el tamaño exacto de cada una.
 */
internal const val INSTRUCTION_SLOT_BYTES = 3

/**
 * Error del lenguaje ASM, al leer el texto o al ejecutarlo. [line] es
 * el número de línea del archivo (la línea 1 es FILETYPE=ASM). Solo es
 * null cuando el fallo no pertenece a una línea concreta, como cuando
 * la ejecución cae sobre bytes de datos. Los textos van en inglés y
 * breves, al estilo MS-DOS.
 */
internal class AsmException(val reason: String, val line: Int? = null) : Exception(reason) {
    fun render(): String = if (line != null) "$reason in line $line" else reason
}

/** Expresión de un operando, ya leída pero todavía sin valor: los símbolos se resuelven en la segunda pasada. */
internal sealed class Expr {
    class Literal(val value: Int) : Expr()
    class Symbol(val name: String) : Expr()
    object Here : Expr()
    class Negate(val inner: Expr) : Expr()
    class Binary(val operator: Char, val left: Expr, val right: Expr) : Expr()
}

/** Operando tal como sale de la primera pasada: las expresiones aún no tienen valor. */
internal sealed class RawOperand {
    class Register(val register: RegisterName) : RawOperand()
    class Immediate(val expr: Expr) : RawOperand()

    /** [base] es BX, SI o DI en el modo indirecto; [address] es la dirección del modo directo. [width] solo viene si el texto dijo BYTE PTR o WORD PTR. */
    class Memory(
        val base: RegisterName?,
        val address: Expr?,
        val width: Int?,
        val segment: RegisterName?
    ) : RawOperand()
}

/** Operando ya resuelto, listo para ejecutarse sin volver a evaluar nada. */
internal sealed class Operand {
    class Register(val register: RegisterName) : Operand()
    class Immediate(val value: Int) : Operand()

    /** Con [base] el desplazamiento sale del registro; sin él se usa [offset]. [segment] ya trae DS si el programa no puso prefijo. */
    class Memory(
        val base: RegisterName?,
        val offset: Int,
        val segment: RegisterName,
        val width: Int?
    ) : Operand()
}

/** Instrucciones del Paso 1. Los 16 saltos condicionales comparten JCC y se distinguen por [Condition]. */
internal enum class Op { MOV, ADD, SUB, CMP, INC, DEC, JMP, JCC, NOP, INT }

/** Condiciones de los saltos; los alias (JZ = JE, JNAE = JB...) se resuelven en el parser. */
internal enum class Condition { O, NO, B, AE, E, NE, BE, A, S, NS, P, NP, L, GE, LE, G }

/**
 * Una instrucción lista para ejecutar. [width] es 8 o 16 según los
 * operandos (0 en NOP, INT y saltos, que no operan sobre datos).
 * [address] es el desplazamiento dentro del segmento del programa.
 */
internal class Instruction(
    val line: Int,
    val address: Int,
    val op: Op,
    val condition: Condition?,
    val operands: List<Operand>,
    val width: Int
) {
    val nextAddress: Int get() = (address + INSTRUCTION_SLOT_BYTES) and 0xFFFF
}

internal class DataBlock(val offset: Int, val bytes: ByteArray)

/**
 * Resultado de leer un archivo .ASM: las instrucciones indexadas por
 * su dirección, los bloques de datos (DB/DW) que hay que copiar a
 * memoria y el punto de entrada. Las instrucciones NO viven en la
 * memoria de la máquina (no hay bytes que poner ahí todavía); un salto
 * a una dirección sin instrucción se detecta en [instructionAt].
 */
internal class ProgramImage(
    val entryOffset: Int,
    val endOffset: Int,
    val dataBlocks: List<DataBlock>,
    private val instructions: Map<Int, Instruction>
) {
    fun instructionAt(offset: Int): Instruction? = instructions[offset]
}
