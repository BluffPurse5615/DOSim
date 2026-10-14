package com.keylerperales07.projectmountains.resimdos.runtime

import com.keylerperales07.projectmountains.resimdos.runtime.shell.ShellContext

/**
 * Environment Manager del DOS Runtime: interpreta la variable PATH y
 * resuelve en qué carpeta real está un programa cuando el usuario lo
 * escribe por nombre (ej. "EDITOR" -> busca EDITOR.COM/.EXE/.BAT
 * primero en el directorio actual y luego en cada carpeta de PATH,
 * en ese orden, como DOS real).
 */
object EnvironmentManager {
    private const val PATH_VARIABLE = "PATH"
    private const val PATH_SEPARATOR = ";"
    private val EXECUTABLE_EXTENSIONS = listOf("COM", "EXE", "BAT")

    fun pathDirectories(shellContext: ShellContext): List<String> {
        val raw = shellContext.environment[PATH_VARIABLE] ?: return emptyList()
        return raw.split(PATH_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Si [nameOrBaseName] ya trae extensión (ej. "AUTOEXEC.BAT"), se busca ese
     * nombre exacto. Si no la trae (ej. "EDITOR"), se prueban .COM/.EXE/.BAT
     * en ese orden. En ambos casos se busca primero en el directorio actual
     * y luego en cada carpeta de PATH.
     */
    fun resolveExecutable(shellContext: ShellContext, nameOrBaseName: String): String? {
        val searchDirectories = listOf(shellContext.currentDirectory) + pathDirectories(shellContext)
        val candidateNames = if (nameOrBaseName.contains('.')) {
            listOf(nameOrBaseName)
        } else {
            EXECUTABLE_EXTENSIONS.map { extension -> "$nameOrBaseName.$extension" }
        }

        for (directory in searchDirectories) {
            for (fileName in candidateNames) {
                val candidate = shellContext.resolvePath("$directory\\$fileName")
                if (shellContext.drive.exists(candidate)) return candidate
            }
        }
        return null
    }
}
