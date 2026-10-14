package com.keylerperales07.projectmountains.resimdos.runtime

/**
 * Formatos virtuales de Simulación Recreada. No son binarios reales
 * de DOS/Windows: son formatos propios interpretados por ReSimCore.
 */
enum class ProgramFormat {
    COM, EXE, BAT, SYS, UNKNOWN;

    companion object {
        fun fromPath(path: String): ProgramFormat {
            val extension = path.substringAfterLast('.', missingDelimiterValue = "").uppercase()
            return when (extension) {
                "COM" -> COM
                "EXE" -> EXE
                "BAT" -> BAT
                "SYS" -> SYS
                else -> UNKNOWN
            }
        }
    }
}
