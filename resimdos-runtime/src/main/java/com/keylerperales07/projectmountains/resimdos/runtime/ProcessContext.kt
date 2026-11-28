package com.keylerperales07.projectmountains.resimdos.runtime

import com.keylerperales07.projectmountains.resimdos.runtime.shell.ShellContext

/**
 * Lo que recibe un [VirtualProgram] al ejecutarse: sus argumentos,
 * la sesión de shell activa (para leer/escribir en la misma unidad
 * y directorio que el usuario), un canal de salida para imprimir
 * en la consola virtual y, para los programas que leen teclas, el
 * búfer de teclado donde la consola deposita lo que escribe el usuario.
 */
class ProcessContext(
    val args: List<String>,
    val shellContext: ShellContext
) {
    private val outputLines = mutableListOf<String>()

    /** Cuántas de [outputLines] ya se le entregaron a la consola con [takeNewLines]. */
    private var deliveredLines = 0

    /** Texto de la línea sin terminar que el programa dejó escrita al detenerse a esperar una tecla. */
    private var prompt = ""

    /** Teclas pendientes: la consola las deposita, el programa las consume. */
    val keyboard = KeyboardBuffer()

    fun writeLine(line: String) {
        outputLines.add(line)
    }

    fun collectedOutput(): List<String> = outputLines

    /**
     * Líneas escritas desde la última vez que se pidieron. Un programa
     * que se detiene a esperar teclas tiene que mostrar lo que ya
     * imprimió antes de que el usuario escriba; esperar al final, como
     * hacen los programas síncronos, dejaría la pantalla en blanco.
     */
    fun takeNewLines(): List<String> {
        val fresh = outputLines.subList(deliveredLines, outputLines.size).toList()
        deliveredLines = outputLines.size
        return fresh
    }

    /** El programa deja aquí lo que imprimió sin salto de línea (su "prompt") antes de esperar una tecla. */
    fun offerPrompt(text: String) {
        prompt = text
    }

    fun takePrompt(): String {
        val text = prompt
        prompt = ""
        return text
    }
}
