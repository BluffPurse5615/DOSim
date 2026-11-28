package com.keylerperales07.projectmountains.resimdos.runtime.asm

import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext
import com.keylerperales07.projectmountains.resimdos.runtime.ProgramFormat
import com.keylerperales07.projectmountains.resimdos.runtime.ProgramStep
import com.keylerperales07.projectmountains.resimdos.runtime.ResumableProgram

/**
 * Un archivo con FILETYPE=ASM ejecutado como programa: lee el texto,
 * prepara la máquina como lo haría DOS con un .COM y corre
 * instrucción por instrucción hasta que el programa termine o necesite
 * una tecla.
 *
 * Es reanudable: cuando una interrupción de teclado no tiene entrada,
 * [advance] guarda todo el estado (la máquina, la imagen del programa
 * y el búfer de salida) en [live], devuelve [ProgramStep.WaitingForKey]
 * y la consola vuelve a llamarlo cuando el usuario envía una línea. La
 * máquina no necesita "congelarse" de ninguna forma especial: su estado
 * completo ya es solo datos (registros, banderas, memoria e IP).
 *
 * Por ahora solo existe el modelo .COM; los .EXE (varios segmentos,
 * PSP completo, pila declarada) llegan en el Paso 6, y entonces la
 * carga de [loadCom] pasa a un ImageLoader propio.
 */
internal class AsmProgram(
    private val sourceText: String,
    private val format: ProgramFormat
) : ResumableProgram {

    /** Todo lo que sobrevive entre un tramo de ejecución y el siguiente. */
    private class LiveProgram(
        val image: ProgramImage,
        val machine: Machine,
        val executor: InstructionExecutor,
        val console: ConsoleOutput
    )

    private var live: LiveProgram? = null

    /**
     * Vía síncrona (la usan los .BAT y AUTOEXEC): ahí nadie puede
     * teclear, así que si el programa pide una tecla se corta con un
     * mensaje en lugar de quedarse esperando para siempre.
     */
    override fun run(context: ProcessContext): Int {
        return when (val step = advance(context)) {
            is ProgramStep.Finished -> step.exitCode
            ProgramStep.WaitingForKey -> {
                val prompt = context.takePrompt()
                if (prompt.isNotEmpty()) context.writeLine(prompt)
                context.writeLine("Keyboard input not available")
                1
            }
        }
    }

    override fun advance(context: ProcessContext): ProgramStep {
        // Si la carga falla no existe LiveProgram todavía, pero el error igual
        // necesita volcar lo que se hubiera alcanzado a imprimir: por eso la
        // consola de salida se crea aparte y se entrega a LiveProgram después.
        var console: ConsoleOutput? = live?.console
        try {
            if (format != ProgramFormat.COM) {
                throw AsmException("Only .COM programs are supported yet")
            }

            val current = live ?: load(context).also { live = it }
            console = current.console
            return runUntilPause(current)
        } catch (e: AsmException) {
            // Primero lo que el programa alcanzó a imprimir, luego el error.
            console?.finish()
            context.writeLine(e.render())
            live = null
            return ProgramStep.Finished(1)
        }
    }

    private fun load(context: ProcessContext): LiveProgram {
        val image = AsmParser().parse(sourceText)
        val machine = Machine()
        loadCom(machine, image)

        val console = ConsoleOutput(context)
        val interrupts = InterruptHandler(machine, console, context.keyboard)
        return LiveProgram(image, machine, InstructionExecutor(machine, interrupts), console)
    }

    /**
     * Deja la máquina como la deja DOS al cargar un .COM: los cuatro
     * segmentos iguales, IP = 100h, pila al final del segmento, y el
     * PSP al inicio con el INT 20h (CD 20) que usa la terminación clásica.
     * Los datos (DB/DW) se copian a memoria; las instrucciones no, porque
     * todavía no hay bytes que ponerles (ver ProgramImage).
     */
    private fun loadCom(machine: Machine, image: ProgramImage) {
        for (segmentRegister in listOf(RegisterName.CS, RegisterName.DS, RegisterName.ES, RegisterName.SS)) {
            machine.writeRegister(segmentRegister, COM_SEGMENT)
        }
        machine.ip = image.entryOffset
        machine.writeRegister(RegisterName.SP, 0xFFFE)
        machine.flags = Machine.IF

        machine.writeByte(COM_SEGMENT, 0, 0xCD)
        machine.writeByte(COM_SEGMENT, 1, 0x20)

        for (block in image.dataBlocks) {
            block.bytes.forEachIndexed { index, byte ->
                machine.writeByte(COM_SEGMENT, block.offset + index, byte.toInt() and 0xFF)
            }
        }
    }

    /** Corre hasta que el programa termine o una interrupción necesite el teclado. */
    private fun runUntilPause(current: LiveProgram): ProgramStep {
        var steps = 0L
        var lastLine: Int? = null

        while (true) {
            // La consola corre cada tramo en el hilo principal: sin este tope, un JMP a sí
            // mismo congelaría la app hasta que Android la mate por no responder (ANR).
            // El conteo es por tramo, no total: cada vez que el programa cede el hilo
            // para esperar una tecla, el tope vuelve a empezar. Se quita cuando la
            // máquina corra fuera del hilo principal.
            if (++steps > MAX_STEPS) throw AsmException("Program exceeded the step limit", lastLine)

            val instruction = current.image.instructionAt(current.machine.ip) ?: throw AsmException(
                "No instruction at %04X:%04X".format(
                    current.machine.readRegister(RegisterName.CS),
                    current.machine.ip
                )
            )
            lastLine = instruction.line

            when (current.executor.execute(instruction)) {
                Flow.CONTINUE -> Unit
                Flow.TERMINATE -> {
                    current.console.finish()
                    live = null
                    return ProgramStep.Finished(0)
                }
                Flow.NEEDS_KEY, Flow.IDLE -> {
                    current.console.suspendForInput()
                    return ProgramStep.WaitingForKey
                }
            }
        }
    }

    companion object {
        /** Segmento donde se carga un .COM; con ORG 100h el programa queda en la dirección lineal 1100h. */
        const val COM_SEGMENT = 0x0100

        const val MAX_STEPS = 5_000_000L
    }
}
