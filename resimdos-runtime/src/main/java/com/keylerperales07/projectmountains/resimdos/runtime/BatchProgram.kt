package com.keylerperales07.projectmountains.resimdos.runtime

import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileSystemException
import com.keylerperales07.projectmountains.resimdos.runtime.shell.CommandInterpreter

/**
 * Un .BAT es una lista de líneas de COMMAND.COM ejecutadas en orden,
 * usando la misma sesión de shell (mismo directorio actual, mismas
 * variables de entorno) que el usuario que lo lanzó.
 *
 * Por defecto, cada línea se "escribe en pantalla" antes de
 * ejecutarse (echo ON, como DOS real). "ECHO OFF" apaga eso para las
 * líneas siguientes; una línea que empieza con "@" nunca se muestra
 * a sí misma, sin importar el estado de echo (por eso "@ECHO OFF" es
 * silencioso incluso al inicio del archivo).
 *
 * %0 se reemplaza por el nombre del propio batch y %1.."%9" por sus
 * argumentos (como en DOS real). Si una línea no es un comando
 * interno, se intenta como programa externo con [programLoader] —
 * igual que hace la consola interactiva — antes de darla por error.
 */
internal class BatchProgram(
    private val scriptContent: String,
    private val programLoader: ProgramLoader,
    private val batchName: String = ""
) : VirtualProgram {

    override fun run(context: ProcessContext): Int {
        val interpreter = CommandInterpreter(context.shellContext)
        val lines = scriptContent.split("\n").map { it.trim() }
        var echoOn = true

        for (rawLine in lines) {
            if (rawLine.isEmpty() || rawLine.startsWith("REM", ignoreCase = true)) continue

            val expandedLine = substituteParameters(rawLine, context.args)
            val suppressEcho = expandedLine.startsWith("@")
            val line = if (suppressEcho) expandedLine.removePrefix("@").trim() else expandedLine

            if (echoOn && !suppressEcho) {
                val prompt = "${context.shellContext.drive.driveLetter}:"
                context.writeLine("$prompt$line")
            }

            when {
                line.equals("ECHO OFF", ignoreCase = true) -> { echoOn = false; continue }
                line.equals("ECHO ON", ignoreCase = true) -> { echoOn = true; continue }
            }

            val result = interpreter.execute(line)
            result.output.forEach { context.writeLine(it) }

            if (result.isError) {
                if (!tryRunAsProgram(line, context)) {
                    context.writeLine(result.errorMessage ?: "Error")
                    return 1
                }
            }
        }
        return 0
    }

    /** Sustituye %0 (nombre del batch) y %1.."%9" (sus argumentos) por su valor real. */
    private fun substituteParameters(line: String, args: List<String>): String {
        var result = line
        for (i in 0 .. 9) {
            val value = if (i == 0) batchName else args.getOrNull(i - 1) ?: ""
            result = result.replace("%$i", value)
        }
        return result
    }

    /** Si la línea no era un comando interno, se busca y ejecuta como .COM/.EXE/.BAT real. */
    private fun tryRunAsProgram(line: String, context: ProcessContext): Boolean {
        val trimmed = line.trim()
        val commandName = trimmed.substringBefore(' ')
        val args = trimmed.split(Regex("\\s+")).drop(1)

        val resolved = try {
            EnvironmentManager.resolveExecutable(context.shellContext, commandName)
        } catch (e: VirtualFileSystemException) {
            null
        } ?: return false

        val process = programLoader.execute(resolved, args, context.shellContext)
        process.output.forEach { context.writeLine(it) }
        return true
    }
}
