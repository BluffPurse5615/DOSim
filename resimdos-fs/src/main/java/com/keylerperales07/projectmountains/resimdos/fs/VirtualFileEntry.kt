package com.keylerperales07.projectmountains.resimdos.fs

/**
 * Representa una entrada dentro del sistema de archivos virtual: un
 * archivo o un directorio (incluida la raíz de la unidad).
 *
 * [name] siempre se guarda en mayúsculas, siguiendo la convención DOS
 * (COMMAND.COM, CONFIG.SYS, AUTOEXEC.BAT, etc.).
 */
data class VirtualFileEntry(
    val name: String,
    val isDirectory: Boolean,
    val attributes: Int,
    val sizeBytes: Long,
    val createdAt: Long,
    val modifiedAt: Long
) {
    val isReadOnly: Boolean get() = FileAttributes.has(attributes, FileAttributes.READ_ONLY)
    val isHidden: Boolean get() = FileAttributes.has(attributes, FileAttributes.HIDDEN)
    val isSystem: Boolean get() = FileAttributes.has(attributes, FileAttributes.SYSTEM)
}
