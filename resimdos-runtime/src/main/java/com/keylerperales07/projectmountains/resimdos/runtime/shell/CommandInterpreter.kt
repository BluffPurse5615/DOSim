package com.keylerperales07.projectmountains.resimdos.runtime.shell

/**
 * COMMAND.COM: recibe una línea escrita por el usuario, la separa en
 * comando + argumentos (respetando comillas) y ejecuta el [Command]
 * correspondiente sobre el [ShellContext]. DOS 1.25 no tiene SET ni
 * variables de entorno de usuario, así que no hay expansión de
 * %VARIABLES% aquí (eso llegó con versiones posteriores de DOS).
 */
class CommandInterpreter(private val context: ShellContext) {

    private val commands: Map<String, Command> = listOf(
        DirCommand(),
        DelCommand(),
        EraseCommand(),
        RenCommand(),
        RenameCommand(),
        TypeCommand(),
        CopyCommand(),
        DateCommand(),
        TimeCommand(),
        RemCommand(),
        PauseCommand()
    ).associateBy { it.name }

    fun execute(line: String): CommandResult {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return CommandResult.ok()

        val (commandTokens, redirection) = extractRedirection(tokenize(trimmed))
        if (commandTokens.isEmpty()) return CommandResult.ok()

        val commandName = commandTokens.first().uppercase()
        val args = commandTokens.drop(1)

        val command = commands[commandName]
        ?: return CommandResult.error("'$commandName' no se reconoce como un comando interno o externo.")

        val result = command.execute(args, context)

        if (redirection != null && !result.isError && !result.clearScreen) {
            return try {
                writeRedirectedOutput(redirection, result.output)
                CommandResult.ok()
            } catch (e: Exception) {
                CommandResult.error(e.message ?: "No se pudo escribir en ${redirection.path}")
            }
        }

        return result
    }

    private data class Redirection(val path: String, val append: Boolean)

    /** Detecta un ">" o ">>" seguido de un nombre de archivo al final de la línea. */
    private fun extractRedirection(tokens: List<String>): Pair<List<String>, Redirection?> {
        val index = tokens.indexOfFirst { it == ">" || it == ">>" }
        if (index == -1) return tokens to null

        val targetPath = tokens.getOrNull(index + 1) ?: return tokens.take(index) to null
        return tokens.take(index) to Redirection(targetPath, append = tokens[index] == ">>")
    }

    private fun writeRedirectedOutput(redirection: Redirection, lines: List<String>) {
        val content = lines.joinToString("\n").let { if (lines.isEmpty()) it else "$it\n" }.toByteArray()
        val path = context.resolvePath(redirection.path)

        if (context.drive.exists(path)) {
            context.drive.writeFile(path, content, append = redirection.append)
        } else {
            context.drive.createFile(path, content)
        }
    }

    /** Separa por espacios respetando texto entre comillas dobles. */
    private fun tokenize(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false

        for (char in line) {
            when {
                char == '"' -> insideQuotes = !insideQuotes
                char.isWhitespace() && !insideQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}
