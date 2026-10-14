package com.keylerperales07.projectmountains.resimdos.runtime

import com.keylerperales07.projectmountains.resimdos.runtime.shell.ShellContext

/**
 * Lo que recibe un [VirtualProgram] al ejecutarse: sus argumentos,
 * la sesión de shell activa (para leer/escribir en la misma unidad
 * y directorio que el usuario) y un canal de salida para imprimir
 * en la consola virtual.
 */
class ProcessContext(
    val args: List<String>,
    val shellContext: ShellContext
) {
    private val outputLines = mutableListOf<String>()

    fun writeLine(line: String) {
        outputLines.add(line)
    }

    fun collectedOutput(): List<String> = outputLines
}
