package com.keylerperales07.projectmountains.resimdos.fs

/**
 * Atributos de archivo al estilo DOS (equivalentes a los de FAT).
 *
 * Se representan como bitmask para poder combinarlos, igual que en
 * ATTRIB.COM: por ejemplo READ_ONLY + HIDDEN.
 */
object FileAttributes {
    const val NONE: Int = 0x00
    const val READ_ONLY: Int = 0x01
    const val HIDDEN: Int = 0x02
    const val SYSTEM: Int = 0x04
    const val DIRECTORY: Int = 0x10
    const val ARCHIVE: Int = 0x20

    fun has(attributes: Int, flag: Int): Boolean = (attributes and flag) == flag

    fun set(attributes: Int, flag: Int): Int = attributes or flag

    fun clear(attributes: Int, flag: Int): Int = attributes and flag.inv()

    /** Representación corta al estilo "ATTRIB", ej: "RHSA" */
    fun toDisplayString(attributes: Int): String {
        val sb = StringBuilder()
        sb.append(if (has(attributes, READ_ONLY)) "R" else "-")
        sb.append(if (has(attributes, HIDDEN)) "H" else "-")
        sb.append(if (has(attributes, SYSTEM)) "S" else "-")
        sb.append(if (has(attributes, DIRECTORY)) "D" else "-")
        sb.append(if (has(attributes, ARCHIVE)) "A" else "-")
        return sb.toString()
    }
}
