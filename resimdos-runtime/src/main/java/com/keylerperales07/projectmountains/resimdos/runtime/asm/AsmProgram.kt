package com.keylerperales07.projectmountains.resimdos.runtime.asm

import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext
import com.keylerperales07.projectmountains.resimdos.runtime.ProgramFormat
import com.keylerperales07.projectmountains.resimdos.runtime.VirtualProgram

/**
 * Un archivo con FILETYPE=ASM ejecutado como programa: lee el texto,
 * prepara la máquina como lo haría DOS con un .COM y corre
 * instrucción por instrucción hasta que el programa termine.
 *
 * Por ahora solo existe el modelo .COM; los .EXE (varios segmentos,
 * PSP completo, pila declarada) llegan en el Paso 6, y entonces la
 * carga de [loadCom] pasa a un ImageLoader propio.
 */
internal class AsmProgram(
    private val sourceText: String,
    private val format: ProgramFormat
) : VirtualProgram {

    override fun run(context: ProcessContext): Int {
        val console = ConsoleOutput(context)
        try {
            if (format != ProgramFormat.COM) {
                throw AsmException("Only .COM programs are supported yet")
            }

            val image = AsmParser().parse(sourceText)
            val machine = Machine()
            loadCom(machine, image)

            val executor = InstructionExecutor(machine, InterruptHandler(machine, console))
            runUntilDone(image, machine, executor)

            console.finish()
            return 0
        } catch (e: AsmException) {
            // Primero lo que el programa alcanzó a imprimir, luego el error.
            console.finish()
            context.writeLine(e.render())
            return 1
        }
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

    private fun runUntilDone(image: ProgramImage, machine: Machine, executor: InstructionExecutor) {
        var steps = 0L
        var lastLine: Int? = null

        while (true) {
            // La consola corre el programa en el hilo principal: sin este tope, un JMP a sí
            // mismo congelaría la app hasta que Android la mate por no responder (ANR).
            // Se quita cuando la máquina sea reanudable (Paso 1b) y pueda ceder el hilo.
            if (++steps > MAX_STEPS) throw AsmException("Program exceeded the step limit", lastLine)

            val instruction = image.instructionAt(machine.ip) ?: throw AsmException(
                "No instruction at %04X:%04X".format(machine.readRegister(RegisterName.CS), machine.ip)
            )
            lastLine = instruction.line

            if (executor.execute(instruction) == Flow.TERMINATE) return
        }
    }

    companion object {
        /** Segmento donde se carga un .COM; con ORG 100h el programa queda en la dirección lineal 1100h. */
        const val COM_SEGMENT = 0x0100

        const val MAX_STEPS = 5_000_000L
    }
}
