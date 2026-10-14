package com.keylerperales07.projectmountains.resimdos.fs

/**
 * Normaliza y divide rutas estilo DOS (separador "\", raíz de la
 * unidad = "\", nombres insensibles a mayúsculas/minúsculas).
 *
 * Acepta tanto "\" como "/" de entrada para comodidad, pero
 * internamente todo se trata como DOS: mayúsculas y "\".
 *
 * No incluye la letra de unidad (eso lo maneja VirtualDrive/ReSimCore);
 * solo la parte de ruta dentro de la unidad, ej. "\GAMES\DOOM.EXE".
 */
object VirtualPath {

    const val SEPARATOR = "\\"
    const val ROOT = "\\"

    /** Devuelve los segmentos de la ruta ya normalizados, sin vacíos. */
    fun segments(path: String): List<String> {
        var normalized = path.replace("/", SEPARATOR).trim()

        // Si viene con letra de unidad ("C:\..."), se descarta aquí:
        // ReSimCore ya sabe a qué unidad enrutar antes de llegar a VirtualDrive.
        if (normalized.length >= 2 && normalized[1] == ':') {
            normalized = normalized.substring(2)
        }

        if (normalized.isEmpty()) normalized = ROOT

        return normalized
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { validateSegment(it, path) }
    }

    /** Reconstruye una ruta canónica ("\A\B\C.TXT") a partir de sus segmentos. */
    fun join(segments: List<String>): String {
        if (segments.isEmpty()) return ROOT
        return SEPARATOR + segments.joinToString(SEPARATOR)
    }

    fun parentOf(path: String): String {
        val segs = segments(path)
        return if (segs.size <= 1) ROOT else join(segs.dropLast(1))
    }

    fun nameOf(path: String): String {
        val segs = segments(path)
        return if (segs.isEmpty()) ROOT else segs.last()
    }

    private fun validateSegment(segment: String, originalPath: String): String {
        if (segment == "." || segment == "..") {
            // Se puede añadir soporte de "." / ".." más adelante si el shell lo necesita.
            throw VirtualFileSystemException.InvalidPathException(originalPath)
        }
        val invalidChars = charArrayOf('/', ':', '*', '?', '"', '<', '>', '|')
        if (segment.any { it in invalidChars }) {
            throw VirtualFileSystemException.InvalidPathException(originalPath)
        }
        return segment.uppercase()
    }
}
