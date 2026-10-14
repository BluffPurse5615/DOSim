package com.keylerperales07.projectmountains.resimdos.runtime.shell

/**
 * Resultado de ejecutar una línea en COMMAND.COM. [output] son las
 * líneas a imprimir en la consola virtual; [errorMessage] se llena
 * cuando el comando falló (ej. "Archivo no encontrado"); [clearScreen]
 * lo usa CLS para indicarle a la UI que borre la pantalla en vez de
 * imprimir texto. [awaitingClockField] lo usan DATE/TIME sin
 * argumentos: le dice a la consola que la SIGUIENTE línea que
 * escriba el usuario no es un comando nuevo, sino el valor nuevo (o
 * vacío para dejarlo igual) de "DATE" o "TIME". [awaitingKeyPress] lo
 * usa PAUSE: la consola no debe pedir un comando nuevo hasta que el
 * usuario mande cualquier línea (se descarta su contenido, como
 * "cualquier tecla" en DOS real).
 */
data class CommandResult(
    val output: List<String> = emptyList(),
    val errorMessage: String? = null,
    val clearScreen: Boolean = false,
    val awaitingClockField: String? = null,
    val awaitingKeyPress: Boolean = false
) {
    val isError: Boolean get() = errorMessage != null

    companion object {
        fun ok(vararg lines: String) = CommandResult(output = lines.toList())
        fun error(message: String) = CommandResult(errorMessage = message)
        fun clear() = CommandResult(clearScreen = true)
        fun awaitingClock(field: String, vararg lines: String) =
        CommandResult(output = lines.toList(), awaitingClockField = field)
        fun pause(message: String) = CommandResult(output = listOf(message), awaitingKeyPress = true)
    }
}
