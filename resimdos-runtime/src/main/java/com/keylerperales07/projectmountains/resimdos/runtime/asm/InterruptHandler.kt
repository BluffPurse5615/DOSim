package com.keylerperales07.projectmountains.resimdos.runtime.asm

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
 */
internal class ConsoleOutput(private val context: ProcessContext) {

    private val pending = StringBuilder()

    fun write(code: Int) {
        when (code) {
            0x0A -> flushLine()
            0x0D -> Unit
            else -> pending.append((code and 0xFF).toChar())
        }
    }

    /** Entrega lo que quede sin terminar (un programa que imprime "Hola$" sin CR LF) al acabar la ejecución. */
    fun finish() {
        if (pending.isNotEmpty()) flushLine()
    }

    private fun flushLine() {
        context.writeLine(pending.toString())
        pending.setLength(0)
    }
}

/**
 * Atiende INT 20h e INT 21h. En este paso solo las funciones de
 * terminar y de salida por pantalla; el resto de INT 21h (teclado en
 * el 1b, archivos con FCB en el 4) se suma aquí mismo, función por
 * función, sin tocar el ejecutor.
 */
internal class InterruptHandler(
    private val machine: Machine,
    private val console: ConsoleOutput
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
            0x02 -> {
                console.write(machine.readRegister(RegisterName.DL))
                Flow.CONTINUE
            }
            0x09 -> {
                printDollarString(line)
                Flow.CONTINUE
            }
            else -> throw AsmException("Unsupported DOS function ${hex(function)}", line)
        }
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
}
