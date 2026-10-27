package com.keylerperales07.projectmountains.resimdos.runtime.shell

import com.keylerperales07.projectmountains.resimdos.fs.VirtualDrive
import com.keylerperales07.projectmountains.resimdos.fs.VirtualPath

/**
 * Estado de una sesión de COMMAND.COM: sobre qué unidad está
 * trabajando y la fecha/hora del sistema (DATE/TIME). DOS 1.25 no
 * tiene subdirectorios, así que currentDirectory siempre es la raíz;
 * se deja el campo por si una versión de DOS posterior lo necesita.
 */
class ShellContext(val drive: VirtualDrive) {

    var currentDirectory: String = VirtualPath.ROOT
        private set

    val environment: MutableMap<String, String> = mutableMapOf()
    val clock: DosClock = DosClock()

    /** Filas de texto que caben en pantalla; la UI lo actualiza antes de cada comando (DIR /P pagina con esto). */
    var screenRows: Int = 25

    fun changeDirectory(newDirectory: String) {
        currentDirectory = newDirectory
    }

    /** Resuelve un argumento de ruta (relativo o absoluto) contra el directorio actual. */
    fun resolvePath(argument: String): String {
        val trimmed = argument.trim()
        val isAbsolute = trimmed.startsWith("\\") ||
                trimmed.startsWith("/") ||
                (trimmed.length >= 2 && trimmed[1] == ':')

        val combined = when {
            isAbsolute -> trimmed
            currentDirectory == VirtualPath.ROOT -> VirtualPath.ROOT + trimmed
            else -> currentDirectory + VirtualPath.SEPARATOR + trimmed
        }
        return VirtualPath.join(VirtualPath.segments(combined))
    }
}