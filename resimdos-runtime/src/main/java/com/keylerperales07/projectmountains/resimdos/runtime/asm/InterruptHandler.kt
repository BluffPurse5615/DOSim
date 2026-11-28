package com.keylerperales07.projectmountains.resimdos.runtime.asm

import com.keylerperales07.projectmountains.resimdos.runtime.KeyboardBuffer
import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext

/**
 * Puente entre los bytes que imprime un programa y el canal de salida
 * del runtime, que solo sabe recibir líneas completas
 * ([ProcessContext.writeLine]). Un programa DOS imprime carácter a
 * carácter y marca el fin de línea con CR LF (0Dh 0Ah), así que aquí se
 * va juntando el texto y se entrega una línea cada vez que llega el LF.
 *
 * El CR (retorno de carro) se descarta: sobre una consola de líneas no
 * tiene efecto visible. Los bytes se leen como ISO-8859-1, igual que
 * el parser los escribió, así que lo que va en un DB sale igual en
 * pantalla; la página de códigos 437 llega con el vídeo (Paso 7).
 *
 * Con entrada de teclado hay un detalle más: cuando el programa se
 * detiene a esperar una tecla, lo que ya imprimió sin salto de línea
 * (su "prompt") se entrega aparte y la línea queda ABIERTA: el usuario
 * escribe justo después, y esa línea ya está en pantalla. El primer LF
 * que imprima el programa después de leer solo cierra esa línea, sin
 * dejar una línea en blanco de más.
 */
internal class ConsoleOutput(private val context: ProcessContext) {

    private val pending = StringBuilder()
    private var lineOpen = false

    fun write(code: Int) {
        when (code) {
            0x0A -> endLine()
            0x0D -> Unit
            else -> pending.append((code and 0xFF).toChar())
        }
    }

    /** Entrega lo que quede sin terminar (un programa que imprime "Hola$" sin CR LF) al acabar la ejecución. */
    fun finish() {
        if (pending.isNotEmpty()) endLine()
    }

    /** El programa va a esperar una tecla: lo escrito hasta ahora sin LF pasa a ser el prompt que ve el usuario. */
    fun suspendForInput() {
        context.offerPrompt(pending.toString())
        pending.setLength(0)
        lineOpen = true
    }

    private fun endLine() {
        if (lineOpen) {
            lineOpen = false
            // La línea que el usuario acaba de teclear ya está en pantalla: este LF solo la cierra.
            if (pending.isEmpty()) return
        }
        context.writeLine(pending.toString())
        pending.setLength(0)
    }
}

/**
 * Atiende INT 20h e INT 21h. En este punto: terminar, salida por
 * pantalla y entrada de teclado (01h, 06h, 07h, 08h, 0Ah, 0Bh, 0Ch).
 * Los archivos con FCB (Paso 4) se suman aquí mismo, función por
 * función, sin tocar el ejecutor.
 *
 * Sobre el "eco": la consola es un cuadro de texto editable y lo que el
 * usuario teclea ya queda a la vista mientras escribe. Por eso 01h NO
 * vuelve a imprimir la tecla, y 07h/08h (que no deben mostrarla) no
 * pueden ocultarla todavía; eso queda para la pantalla de celdas del
 * Paso 7, donde las teclas llegan una a una.
 */
internal class InterruptHandler(
    private val machine: Machine,
    private val console: ConsoleOutput,
    private val keyboard: KeyboardBuffer
) {

    fun handle(number: Int, line: Int): Flow = when (number) {
        0x20 -> Flow.TERMINATE
        0x21 -> dosFunction(line)
        else -> throw AsmException("Unsupported interrupt ${hex(number)}", line)
    }

    private fun dosFunction(line: Int): Flow {
        val function = machine.readRegister(RegisterName.AH)
        return when (function) {
            0x00 -> Flow.TERMINATE
            0x01, 0x07, 0x08 -> readKey()
            0x02 -> {
                console.write(machine.readRegister(RegisterName.DL))
                Flow.CONTINUE
            }
            0x06 -> directConsole()
            0x09 -> {
                printDollarString(line)
                Flow.CONTINUE
            }
            0x0A -> readBufferedLine()
            0x0B -> inputStatus()
            0x0C -> flushThenRead(line)
            else -> throw AsmException("Unsupported DOS function ${hex(function)}", line)
        }
    }

    /** 01h, 07h y 08h: esperan una tecla y la devuelven en AL. Sin tecla, la instrucción se repetirá al reanudar. */
    private fun readKey(): Flow {
        val key = keyboard.poll() ?: return Flow.NEEDS_KEY
        machine.writeRegister(RegisterName.AL, key)
        return Flow.CONTINUE
    }

    /** 06h: con DL distinto de FFh imprime DL; con FFh consulta el teclado sin esperar (ZF = 1 si no hay tecla). */
    private fun directConsole(): Flow {
        val dl = machine.readRegister(RegisterName.DL)
        if (dl != 0xFF) {
            console.write(dl)
            return Flow.CONTINUE
        }

        val key = keyboard.poll()
        if (key == null) {
            machine.setFlag(Machine.ZF, true)
            machine.writeRegister(RegisterName.AL, 0)
            return Flow.IDLE
        }
        machine.setFlag(Machine.ZF, false)
        machine.writeRegister(RegisterName.AL, key)
        return Flow.CONTINUE
    }

    /**
     * 0Bh: AL = FFh si hay una tecla esperando, 00h si no. Nunca bloquea,
     * pero un programa que la consulta en bucle ("espera una tecla")
     * dejaría la app girando sin dar paso al usuario; por eso, cuando no
     * hay tecla, se cede el control y el programa queda en pausa hasta
     * que el usuario envíe una línea.
     */
    private fun inputStatus(): Flow {
        val available = !keyboard.isEmpty()
        machine.writeRegister(RegisterName.AL, if (available) 0xFF else 0x00)
        return if (available) Flow.CONTINUE else Flow.IDLE
    }

    /**
     * 0Ah: lee una línea completa al búfer de DS:DX. Formato del búfer:
     * byte 0 = tamaño máximo (incluye el CR final), byte 1 = cuántos
     * caracteres se leyeron (lo escribe DOS), y desde el byte 2 los
     * caracteres terminados en CR. Lo que no cabe se descarta.
     */
    private fun readBufferedLine(): Flow {
        val segment = machine.readRegister(RegisterName.DS)
        val base = machine.readRegister(RegisterName.DX)
        val capacity = machine.readByte(segment, base)
        if (capacity == 0) return Flow.CONTINUE
        if (!keyboard.hasCompleteLine()) return Flow.NEEDS_KEY

        var count = 0
        while (true) {
            val key = keyboard.poll() ?: break
            if (key == KeyboardBuffer.ENTER) break
            if (count < capacity - 1) {
                machine.writeByte(segment, base + 2 + count, key)
                count++
            }
        }
        machine.writeByte(segment, base + 1, count)
        machine.writeByte(segment, base + 2 + count, KeyboardBuffer.ENTER)
        return Flow.CONTINUE
    }

    /**
     * 0Ch: vacía el búfer de teclado y ejecuta la función que indica AL
     * (solo 01h, 06h, 07h, 08h o 0Ah; con otra, AL = 0 y no hace nada).
     * Como hace DOS, redirige copiando AL en AH: así, si la función
     * pedida tiene que esperar y la instrucción se repite al reanudar,
     * ya no se ejecuta como 0Ch y no borra la tecla recién llegada.
     */
    private fun flushThenRead(line: Int): Flow {
        keyboard.clear()
        val requested = machine.readRegister(RegisterName.AL)
        if (requested !in KEYBOARD_FUNCTIONS) {
            machine.writeRegister(RegisterName.AL, 0)
            return Flow.CONTINUE
        }
        machine.writeRegister(RegisterName.AH, requested)
        return dosFunction(line)
    }

    /**
     * Función 09h: imprime desde DS:DX hasta el primer '$' (que no se
     * imprime). Si un programa olvida el '$', DOS real seguía
     * imprimiendo memoria sin parar; aquí se corta tras recorrer los 64 KB
     * del segmento para no colgar la consola.
     */
    private fun printDollarString(line: Int) {
        val segment = machine.readRegister(RegisterName.DS)
        var offset = machine.readRegister(RegisterName.DX)

        for (scanned in 0..0xFFFF) {
            val code = machine.readByte(segment, offset)
            if (code == '$'.code) return
            console.write(code)
            offset = (offset + 1) and 0xFFFF
        }
        throw AsmException("Missing \$ terminator", line)
    }

    private fun hex(value: Int): String = "%02X".format(value) + "h"

    private companion object {
        val KEYBOARD_FUNCTIONS = setOf(0x01, 0x06, 0x07, 0x08, 0x0A)
    }
}
