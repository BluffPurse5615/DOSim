package com.keylerperales07.projectmountains.resimdos.runtime.asm

/** Tres familias de registros del 8086; la tabla de opcodes del Paso 2 las distinguirá por este grupo. */
internal enum class RegisterGroup { GENERAL16, GENERAL8, SEGMENT }

/**
 * Los registros que el lenguaje ASM reconoce por nombre. [code] es el
 * número de 3 bits que el 8086 real usa dentro de sus instrucciones
 * (AX=0, CX=1, DX=2, BX=3, SP=4, BP=5, SI=6, DI=7; AL..BL=0..3 y
 * AH..BH=4..7; ES=0, CS=1, SS=2, DS=3). Se respeta ese orden desde
 * ahora para que el ensamblador a bytes de la etapa binaria no tenga
 * que traducir nada.
 */
internal enum class RegisterName(val width: Int, val code: Int, val group: RegisterGroup) {
    AX(16, 0, RegisterGroup.GENERAL16),
    CX(16, 1, RegisterGroup.GENERAL16),
    DX(16, 2, RegisterGroup.GENERAL16),
    BX(16, 3, RegisterGroup.GENERAL16),
    SP(16, 4, RegisterGroup.GENERAL16),
    BP(16, 5, RegisterGroup.GENERAL16),
    SI(16, 6, RegisterGroup.GENERAL16),
    DI(16, 7, RegisterGroup.GENERAL16),

    AL(8, 0, RegisterGroup.GENERAL8),
    CL(8, 1, RegisterGroup.GENERAL8),
    DL(8, 2, RegisterGroup.GENERAL8),
    BL(8, 3, RegisterGroup.GENERAL8),
    AH(8, 4, RegisterGroup.GENERAL8),
    CH(8, 5, RegisterGroup.GENERAL8),
    DH(8, 6, RegisterGroup.GENERAL8),
    BH(8, 7, RegisterGroup.GENERAL8),

    ES(16, 0, RegisterGroup.SEGMENT),
    CS(16, 1, RegisterGroup.SEGMENT),
    SS(16, 2, RegisterGroup.SEGMENT),
    DS(16, 3, RegisterGroup.SEGMENT);

    companion object {
        private val byName = entries.associateBy { it.name }

        /** Devuelve el registro con ese nombre (sin distinguir mayúsculas) o null si no es un registro. */
        fun fromText(text: String): RegisterName? = byName[text.uppercase()]
    }
}

/**
 * El estado de la máquina virtual y nada más: memoria de 1 MB,
 * registros y banderas. No sabe leer instrucciones ni qué significa
 * una interrupción; eso lo hacen [InstructionExecutor] e
 * [InterruptHandler]. Esa separación es la que permitirá, más
 * adelante, cambiar el origen del código (texto o bytes reales) sin
 * tocar la máquina.
 *
 * Las dos reglas del 8086 que importan aquí: la dirección lineal es
 * segmento x 16 + desplazamiento y se corta a 20 bits (la memoria da
 * la vuelta en 1 MB), y las palabras se guardan con el byte bajo
 * primero.
 */
internal class Machine {

    companion object {
        const val MEMORY_SIZE = 0x100000

        // Posición de cada bandera dentro de la palabra de banderas del 8086.
        const val CF = 0x0001
        const val PF = 0x0004
        const val AF = 0x0010
        const val ZF = 0x0040
        const val SF = 0x0080
        const val TF = 0x0100
        const val IF = 0x0200
        const val DF = 0x0400
        const val OF = 0x0800
    }

    val memory = ByteArray(MEMORY_SIZE)

    // Se guardan por código de registro (ver RegisterName.code), no por nombre.
    private val general = IntArray(8)
    private val segments = IntArray(4)

    var ip: Int = 0
        set(value) { field = value and 0xFFFF }

    var flags: Int = 0

    fun readRegister(register: RegisterName): Int = when (register.group) {
        RegisterGroup.GENERAL16 -> general[register.code]
        RegisterGroup.GENERAL8 ->
            if (register.code < 4) general[register.code] and 0xFF
            else (general[register.code - 4] shr 8) and 0xFF
        RegisterGroup.SEGMENT -> segments[register.code]
    }

    fun writeRegister(register: RegisterName, value: Int) {
        when (register.group) {
            RegisterGroup.GENERAL16 -> general[register.code] = value and 0xFFFF
            RegisterGroup.GENERAL8 -> {
                // AL/CL/DL/BL son la mitad baja de AX/CX/DX/BX; AH/CH/DH/BH, la alta.
                val slot = register.code and 3
                val byte = value and 0xFF
                general[slot] = if (register.code < 4) {
                    (general[slot] and 0xFF00) or byte
                } else {
                    (general[slot] and 0x00FF) or (byte shl 8)
                }
            }
            RegisterGroup.SEGMENT -> segments[register.code] = value and 0xFFFF
        }
    }

    fun flag(mask: Int): Boolean = (flags and mask) != 0

    fun setFlag(mask: Int, on: Boolean) {
        flags = if (on) flags or mask else flags and mask.inv()
    }

    fun linearAddress(segment: Int, offset: Int): Int =
        (((segment and 0xFFFF) shl 4) + (offset and 0xFFFF)) and 0xFFFFF

    fun readByte(segment: Int, offset: Int): Int =
        memory[linearAddress(segment, offset)].toInt() and 0xFF

    fun writeByte(segment: Int, offset: Int, value: Int) {
        memory[linearAddress(segment, offset)] = value.toByte()
    }

    /** Palabra en orden little-endian; si el desplazamiento es FFFFh, el byte alto sale de la posición 0000h del mismo segmento. */
    fun readWord(segment: Int, offset: Int): Int =
        readByte(segment, offset) or (readByte(segment, offset + 1) shl 8)

    fun writeWord(segment: Int, offset: Int, value: Int) {
        writeByte(segment, offset, value and 0xFF)
        writeByte(segment, offset + 1, (value shr 8) and 0xFF)
    }
}
