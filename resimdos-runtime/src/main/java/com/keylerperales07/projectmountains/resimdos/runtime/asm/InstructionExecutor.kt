package com.keylerperales07.projectmountains.resimdos.runtime.asm

/** Qué hace el bucle de ejecución después de una instrucción. */
internal enum class Flow { CONTINUE, TERMINATE }

/**
 * Ejecuta una [Instruction] sobre la [Machine]. Aquí vive lo que cada
 * instrucción HACE (mover datos, sumar, decidir un salto) y cómo deja
 * las banderas; el estado en sí está en Machine y las interrupciones
 * en [InterruptHandler].
 *
 * Los operandos llegan ya validados por el parser (combinación
 * permitida, ancho conocido, inmediato dentro de rango), así que aquí
 * no se vuelven a comprobar.
 */
internal class InstructionExecutor(
    private val machine: Machine,
    private val interrupts: InterruptHandler
) {

    fun execute(instruction: Instruction): Flow {
        val operands = instruction.operands
        val width = instruction.width

        // Por defecto se sigue con la casilla siguiente; los saltos lo sobrescriben abajo.
        machine.ip = instruction.nextAddress

        when (instruction.op) {
            Op.NOP -> Unit
            Op.MOV -> write(operands[0], width, read(operands[1], width))
            Op.ADD -> write(operands[0], width, add(read(operands[0], width), read(operands[1], width), width))
            Op.SUB -> write(operands[0], width, subtract(read(operands[0], width), read(operands[1], width), width))
            Op.CMP -> subtract(read(operands[0], width), read(operands[1], width), width)
            Op.INC -> step(operands[0], width, increase = true)
            Op.DEC -> step(operands[0], width, increase = false)
            Op.JMP -> machine.ip = jumpTarget(operands[0])
            Op.JCC -> {
                val condition = checkNotNull(instruction.condition) { "JCC sin condición" }
                if (conditionHolds(condition)) machine.ip = jumpTarget(operands[0])
            }
            Op.INT -> {
                val number = (operands[0] as Operand.Immediate).value
                return interrupts.handle(number, instruction.line)
            }
        }
        return Flow.CONTINUE
    }

    private fun jumpTarget(operand: Operand): Int = (operand as Operand.Immediate).value and 0xFFFF

    // ---------------------------------------------------------------- acceso a operandos

    private fun read(operand: Operand, width: Int): Int = when (operand) {
        is Operand.Register -> machine.readRegister(operand.register)
        is Operand.Immediate -> operand.value and maskOf(width)
        is Operand.Memory -> {
            val segment = machine.readRegister(operand.segment)
            val offset = effectiveOffset(operand)
            if (width == 8) machine.readByte(segment, offset) else machine.readWord(segment, offset)
        }
    }

    private fun write(operand: Operand, width: Int, value: Int) {
        when (operand) {
            is Operand.Register -> machine.writeRegister(operand.register, value)
            is Operand.Memory -> {
                val segment = machine.readRegister(operand.segment)
                val offset = effectiveOffset(operand)
                if (width == 8) machine.writeByte(segment, offset, value) else machine.writeWord(segment, offset, value)
            }
            is Operand.Immediate -> throw IllegalStateException("Destino inmediato: el parser debió rechazarlo")
        }
    }

    private fun effectiveOffset(operand: Operand.Memory): Int {
        val base = operand.base
        return if (base != null) machine.readRegister(base) else operand.offset
    }

    // ---------------------------------------------------------------- aritmética y banderas

    private fun maskOf(width: Int): Int = if (width == 8) 0xFF else 0xFFFF

    private fun signBitOf(width: Int): Int = if (width == 8) 0x80 else 0x8000

    private fun add(a: Int, b: Int, width: Int): Int {
        val full = a + b
        val result = full and maskOf(width)
        machine.setFlag(Machine.CF, full > maskOf(width))
        machine.setFlag(Machine.AF, ((a xor b xor result) and 0x10) != 0)
        // Desbordamiento con signo: los dos sumandos tienen el mismo signo y el resultado el contrario.
        machine.setFlag(Machine.OF, ((a xor result) and (b xor result) and signBitOf(width)) != 0)
        setResultFlags(result, width)
        return result
    }

    private fun subtract(a: Int, b: Int, width: Int): Int {
        val result = (a - b) and maskOf(width)
        // En una resta CF es el "préstamo": ocurre cuando el sustraendo es mayor que el minuendo sin signo.
        machine.setFlag(Machine.CF, a < b)
        machine.setFlag(Machine.AF, ((a xor b xor result) and 0x10) != 0)
        // Desbordamiento con signo: los operandos tienen signo distinto y el resultado cambia el del minuendo.
        machine.setFlag(Machine.OF, ((a xor b) and (a xor result) and signBitOf(width)) != 0)
        setResultFlags(result, width)
        return result
    }

    /** ZF, SF y PF dependen solo del resultado; PF mira únicamente el byte bajo, aunque la operación sea de 16 bits. */
    private fun setResultFlags(result: Int, width: Int) {
        machine.setFlag(Machine.ZF, result == 0)
        machine.setFlag(Machine.SF, (result and signBitOf(width)) != 0)
        machine.setFlag(Machine.PF, Integer.bitCount(result and 0xFF) % 2 == 0)
    }

    /** INC y DEC actualizan todas las banderas menos CF, que conservan tal como estaba. */
    private fun step(operand: Operand, width: Int, increase: Boolean) {
        val carryBefore = machine.flag(Machine.CF)
        val current = read(operand, width)
        val result = if (increase) add(current, 1, width) else subtract(current, 1, width)
        machine.setFlag(Machine.CF, carryBefore)
        write(operand, width, result)
    }

    private fun conditionHolds(condition: Condition): Boolean {
        val carry = machine.flag(Machine.CF)
        val zero = machine.flag(Machine.ZF)
        val sign = machine.flag(Machine.SF)
        val overflow = machine.flag(Machine.OF)
        val parity = machine.flag(Machine.PF)

        return when (condition) {
            Condition.O -> overflow
            Condition.NO -> !overflow
            Condition.B -> carry
            Condition.AE -> !carry
            Condition.E -> zero
            Condition.NE -> !zero
            Condition.BE -> carry || zero
            Condition.A -> !carry && !zero
            Condition.S -> sign
            Condition.NS -> !sign
            Condition.P -> parity
            Condition.NP -> !parity
            Condition.L -> sign != overflow
            Condition.GE -> sign == overflow
            Condition.LE -> zero || sign != overflow
            Condition.G -> !zero && sign == overflow
        }
    }
}
