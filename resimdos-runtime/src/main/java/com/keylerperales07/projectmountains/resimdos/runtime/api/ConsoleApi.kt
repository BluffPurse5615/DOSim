package com.keylerperales07.projectmountains.resimdos.runtime.api

import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext

/**
 * Salida de consola para programas virtuales. Por ahora solo escribe
 * (equivalente a las funciones de salida de INT 21h); la entrada de
 * teclado interactiva se añadirá cuando la UI tenga un canal de
 * entrada para procesos en ejecución.
 */
class ConsoleApi(private val processContext: ProcessContext) {

    fun print(text: String) {
        processContext.writeLine(text)
    }

    fun println(text: String = "") {
        processContext.writeLine(text)
    }
}
