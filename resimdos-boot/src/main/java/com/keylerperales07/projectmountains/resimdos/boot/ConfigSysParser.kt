package com.keylerperales07.projectmountains.resimdos.boot

/**
 * Interpreta el texto de un CONFIG.SYS. Soporta las directivas
 * básicas: FILES, BUFFERS, LASTDRIVE, SHELL y DEVICE (uno por
 * línea, puede repetirse). Líneas vacías o que empiezan con REM
 * se ignoran, igual que en DOS real.
 */
object ConfigSysParser {

    fun parse(content: String): ConfigSettings {
        var files = 8
        var buffers = 15
        var lastDrive = 'E'
        var shellPath: String? = null
        val devices = mutableListOf<String>()

        content.split("\n").forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("REM", ignoreCase = true)) return@forEach

            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) return@forEach

            val directive = line.substring(0, separatorIndex).trim().uppercase()
            val value = line.substring(separatorIndex + 1).trim()

            when (directive) {
                "FILES" -> value.toIntOrNull()?.let { files = it }
                "BUFFERS" -> value.toIntOrNull()?.let { buffers = it }
                "LASTDRIVE" -> value.firstOrNull()?.let { lastDrive = it.uppercaseChar() }
                "SHELL" -> shellPath = value
                "DEVICE", "DEVICEHIGH" -> devices.add(value)
            }
        }

        return ConfigSettings(
            files = files,
            buffers = buffers,
            lastDrive = lastDrive,
            shellPath = shellPath,
            devices = devices.toList()
        )
    }
}
