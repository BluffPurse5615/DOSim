package com.keylerperales07.projectmountains.resimdos.runtime.shell

/**
 * Resultado de ejecutar una línea en COMMAND.COM. [output] son las
 * líneas a imprimir; [errorMessage] se llena si el comando falló
 * (puede traer varias líneas, ej. el error y debajo la sintaxis);
 * [clearScreen] pide a la UI borrar la pantalla.
 *
 * [awaitingClockField] lo usan DATE/TIME sin argumentos: la SIGUIENTE
 * línea del usuario es el valor nuevo, no un comando. [awaitingKeyPress]
 * lo usan PAUSE y las salidas paginadas: la consola no pide comando
 * hasta recibir cualquier línea (su contenido se descarta).
 *
 * [pendingLines] son las líneas de una salida paginada (DIR /P) que
 * todavía no se mostraron; la UI las va soltando página por página
 * cada vez que el usuario "pulsa una tecla".
 */
data class CommandResult(
    val output: List<String> = emptyList(),
    val errorMessage: String? = null,
    val clearScreen: Boolean = false,
    val awaitingClockField: String? = null,
    val awaitingKeyPress: Boolean = false,
    val pendingLines: List<String> = emptyList()
) {
    val isError: Boolean get() = errorMessage != null

    companion object {
        const val PRESS_KEY_PROMPT = "Strike a key when ready . . ."

        fun ok(vararg lines: String) = CommandResult(output = lines.toList())
        fun error(message: String) = CommandResult(errorMessage = message)
        fun clear() = CommandResult(clearScreen = true)

        fun awaitingClock(field: String, vararg lines: String) =
            CommandResult(output = lines.toList(), awaitingClockField = field)

        fun pause(message: String) =
            CommandResult(output = listOf(message), awaitingKeyPress = true)

        /** Muestra [pageRows] líneas y deja el resto en [pendingLines]. */
        fun paged(lines: List<String>, pageRows: Int): CommandResult {
            if (lines.size <= pageRows) return CommandResult(output = lines)
            return CommandResult(
                output = lines.take(pageRows),
                pendingLines = lines.drop(pageRows),
                awaitingKeyPress = true
            )
        }
    }
}