package com.keylerperales07.projectmountains.resimdos.runtime

/**
 * Cola de teclas que esperan a ser leídas por un programa, como el
 * búfer de teclado del BIOS de un PC.
 *
 * La consola de ReSim DOS trabaja por líneas: el usuario escribe y
 * confirma con Enter. Por eso las teclas no llegan una a una sino por
 * líneas: [pushLine] deposita los caracteres de la línea seguidos del
 * CR (0Dh) que produce la tecla Enter. Un programa que lee "una tecla"
 * recibe así la primera letra, y el Enter queda esperando en el búfer
 * para la lectura siguiente, igual que en un teclado real.
 */
class KeyboardBuffer {

    private val keys = ArrayDeque<Int>()

    /** Deposita la línea tecleada más su Enter. Lo que no cabe en un byte llega como '?'. */
    fun pushLine(text: String) {
        for (ch in text) keys.addLast(if (ch.code <= 0xFF) ch.code else '?'.code)
        keys.addLast(ENTER)
    }

    /** Saca la tecla más antigua, o null si no hay ninguna. */
    fun poll(): Int? = keys.removeFirstOrNull()

    fun isEmpty(): Boolean = keys.isEmpty()

    /** Hay al menos un Enter en la cola, es decir, una línea entera lista para INT 21h función 0Ah. */
    fun hasCompleteLine(): Boolean = keys.contains(ENTER)

    fun clear() {
        keys.clear()
    }

    companion object {
        const val ENTER = 0x0D
    }
}
