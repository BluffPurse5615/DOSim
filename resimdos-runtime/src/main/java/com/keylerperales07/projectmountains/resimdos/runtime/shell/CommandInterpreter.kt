package com.keylerperales07.projectmountains.resimdos.runtime.shell

import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileSystemException

/**
 * COMMAND.COM: recibe una línea escrita por el usuario, la separa en
 * comando + argumentos (respetando comillas) y ejecuta el [Command]
 * correspondiente sobre el [ShellContext]. DOS 1.25 no tiene SET ni
 * variables de entorno de usuario, así que no hay expansión de
 * %VARIABLES% aquí (eso llegó con versiones posteriores de DOS).
 *
 * Los errores de uso y los del sistema de archivos que lance un
 * comando se convierten aquí en un CommandResult de error, para que
 * ninguno llegue a tumbar la consola.
 */
class CommandInterpreter(private val context: ShellContext) {

    private companion object {
        const val BAD_COMMAND = "Bad command or file name"
    }

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

        // DOS acepta el modificador pegado al comando: "DIR/W" es "DIR /W".
        val firstToken = commandTokens.first()
        val slash = firstToken.indexOf('/')
        val commandName = (if (slash > 0) firstToken.substring(0, slash) else firstToken).uppercase()
        val args = (if (slash > 0) listOf(firstToken.substring(slash)) else emptyList()) + commandTokens.drop(1)

        val command = commands[commandName]
            ?: return CommandResult.error(BAD_COMMAND)

        val result = try {
            command.execute(args, context)
        } catch (e: UsageException) {
            val message = if (e.showSyntax) "${e.message}\nSintaxis: ${command.syntax}" else e.message ?: "Error"
            return CommandResult.error(message)
        } catch (e: VirtualFileSystemException) {
            return CommandResult.error(e.message ?: BAD_COMMAND)
        } catch (e: Exception) {
            // Cualquier otro fallo al ejecutar un comando se ve igual que en DOS.
            return CommandResult.error(BAD_COMMAND)
        }

        if (redirection != null && !result.isError && !result.clearScreen) {
            return try {
                // A un archivo va la salida completa, sin paginar.
                writeRedirectedOutput(redirection, result.output + result.pendingLines)
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