package com.keylerperales07.projectmountains.resimdos.runtime.shell

/**
 * Un comando interno de COMMAND.COM. Recibe los argumentos ya
 * separados (sin el nombre del comando) y el [ShellContext] de la
 * sesión. [syntax] es la línea de uso tal como se muestra al usuario
 * cuando se equivoca con los argumentos.
 *
 * Un comando puede lanzar [UsageException] o una excepción del
 * sistema de archivos; CommandInterpreter las traduce a error.
 */
interface Command {
    val name: String
    val syntax: String
    fun execute(args: List<String>, context: ShellContext): CommandResult
}