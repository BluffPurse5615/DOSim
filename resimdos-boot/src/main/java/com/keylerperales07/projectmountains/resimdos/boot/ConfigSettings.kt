package com.keylerperales07.projectmountains.resimdos.boot

/**
 * Ajustes leídos de CONFIG.SYS. No todos tienen efecto real todavía
 * (ej. BUFFERS no cambia nada en el motor), pero se conservan para
 * que un futuro comando (ej. algo como "MEM" o un panel de sistema)
 * pueda mostrarlos, igual que en DOS real.
 */
data class ConfigSettings(
    val files: Int = 8,
    val buffers: Int = 15,
    val lastDrive: Char = 'E',
    val shellPath: String? = null,
    val devices: List<String> = emptyList()
)
