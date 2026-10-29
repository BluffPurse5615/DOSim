package com.keylerperales07.projectmountains.resimdos.runtime.shell

/**
 * Error de uso de un comando: argumentos de más o de menos, un
 * modificador que no existe, una unidad que no está montada.
 * CommandInterpreter lo convierte en un CommandResult de error;
 * si [showSyntax] es true le pega debajo la línea "Sintaxis: ...".
 */
class UsageException(message: String, val showSyntax: Boolean = true) : Exception(message)

/** Un argumento de archivo partido en lo que DOS 1.25 entiende: [pattern] puede traer * y ?. */
internal class FileSpec(val pattern: String)

/**
 * Separa los modificadores (/P, /W...) del resto de los argumentos.
 * Acepta "/W/P" pegados. Cualquier letra fuera de [allowed] corta el comando.
 */
internal fun splitSwitches(args: List<String>, allowed: Set<Char>): Pair<Set<Char>, List<String>> {
    val found = linkedSetOf<Char>()
    val rest = mutableListOf<String>()
    for (arg in args) {
        if (!arg.startsWith("/")) {
            rest.add(arg)
            continue
        }
        for (piece in arg.split('/').filter { it.isNotEmpty() }) {
            val letter = piece.singleOrNull()?.uppercaseChar()
            if (letter == null || letter !in allowed) throw UsageException("Modificador no válido - /$piece")
            found.add(letter)
        }
    }
    return found to rest
}

/**
 * Descompone "[unidad:][ruta]nombre". Solo existe la unidad montada y
 * no hay subdirectorios, así que una unidad distinta o una ruta con
 * carpetas se rechaza aquí, antes de tocar el disco.
 */
internal fun parseFileSpec(argument: String, context: ShellContext): FileSpec {
    var text = argument.trim()
    if (text.length >= 2 && text[1] == ':') {
        val letter = text[0].uppercaseChar()
        if (letter != context.drive.driveLetter.uppercaseChar()) {
            throw UsageException("Unidad no válida - $letter:", showSyntax = false)
        }
        text = text.substring(2)
    }
    val lastSeparator = text.lastIndexOf('\\')
    val folders = if (lastSeparator > 0) text.substring(0, lastSeparator) else ""
    if (folders.isNotEmpty()) throw UsageException("Ruta no encontrada - $argument", showSyntax = false)
    return FileSpec(text.substring(lastSeparator + 1))
}

/**
 * Comodines de nombre de archivo estilo DOS. El nombre y la extensión
 * se comparan por separado: "*" completa lo que falte del campo y "?"
 * vale por una letra (o por el relleno en blanco al final del campo).
 * Un "*" solo se toma como "*.*".
 */
internal object Wildcard {

    fun hasWildcard(text: String): Boolean = text.any { it == '*' || it == '?' }

    /**
     * Con [anyExtensionIfOmitted] un patrón sin punto ("CHKDSK") busca ese nombre con
     * cualquier extensión, que es lo que hace DIR. DEL, REN y TYPE lo dejan apagado:
     * ahí "CHKDSK" es solo el archivo sin extensión.
     */
    fun matches(pattern: String, name: String, anyExtensionIfOmitted: Boolean = false): Boolean {
        val normalized = normalize(pattern)
        val (patternName, patternExt) = split(normalized)
        val ext = if (anyExtensionIfOmitted && '.' !in normalized) "*" else patternExt
        val (realName, realExt) = split(name.uppercase())
        return fieldMatches(patternName, realName) && fieldMatches(ext, realExt)
    }

    /** Nombre que resulta de aplicar un patrón destino (REN *.TXT *.BAK) a un archivo origen. */
    fun apply(destinationPattern: String, sourceName: String): String {
        val (patternName, patternExt) = split(normalize(destinationPattern))
        val (sourceBase, sourceExt) = split(sourceName.uppercase())
        val base = fill(patternName, sourceBase)
        val ext = fill(patternExt, sourceExt)
        return if (ext.isEmpty()) base else "$base.$ext"
    }

    private fun normalize(pattern: String): String =
        pattern.uppercase().let { if (it == "*") "*.*" else it }

    private fun split(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot < 0) name to "" else name.substring(0, dot) to name.substring(dot + 1)
    }

    private fun fieldMatches(pattern: String, text: String): Boolean {
        var i = 0
        for (p in pattern) {
            if (p == '*') return true
            if (i >= text.length) {
                if (p != '?') return false
            } else if (p != '?' && p != text[i]) {
                return false
            }
            i++
        }
        return i >= text.length
    }

    private fun fill(pattern: String, source: String): String {
        val out = StringBuilder()
        for ((i, p) in pattern.withIndex()) {
            when (p) {
                '*' -> {
                    if (i < source.length) out.append(source, i, source.length)
                    return out.toString()
                }
                '?' -> if (i < source.length) out.append(source[i])
                else -> out.append(p)
            }
        }
        return out.toString()
    }
}