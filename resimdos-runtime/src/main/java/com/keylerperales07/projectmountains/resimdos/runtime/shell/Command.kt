package com.keylerperales07.projectmountains.resimdos.runtime.shell

/**
 * Un comando de COMMAND.COM (DIR, CD, MD, ...). Cada implementación
 * recibe los argumentos ya separados (sin el nombre del comando) y
 * el [ShellContext] de la sesión actual.
 */
interface Command {
    val name: String
    fun execute(args: List<String>, context: ShellContext): CommandResult
}
