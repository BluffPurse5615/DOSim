package com.keylerperales07.projectmountains.resimdos.runtime

import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileSystemException
import com.keylerperales07.projectmountains.resimdos.runtime.shell.ShellContext

/**
 * El Program Loader del DOS Runtime: dado un path a un archivo
 * ejecutable virtual, decide cómo correrlo y lo registra como
 * [VirtualProcess] en el [ProcessManager].
 *
 * - .BAT -> siempre se ejecuta línea por línea (BatchProgram)
 * - .COM/.EXE/.SYS -> si la PRIMERA línea del archivo dice
 *   "FILETYPE=BAT" (sin importar mayúsculas/espacios), se ejecuta
 *   línea por línea igual que un .BAT, saltándose esa línea de
 *   declaración. Así cualquier archivo, aunque su extensión sea
 *   .COM, se puede escribir como texto con comandos reales.
 *   Si no trae esa declaración, se busca en [ProgramRegistry]; si no
 *   está registrado, se usa un programa genérico de respaldo (ver
 *   [ProgramRegistry.create]).
 */
class ProgramLoader(
    private val processManager: ProcessManager,
    private val programRegistry: ProgramRegistry
) {

    fun execute(path: String, args: List<String>, shellContext: ShellContext): VirtualProcess {
        val process = processManager.start(path)

        val program = try {
            resolveProgram(path, shellContext)
        } catch (e: Exception) {
            processManager.finish(process.pid, exitCode = 1, output = listOf(e.message ?: "Error"))
            return processManager.get(process.pid)!!
        }

        val context = ProcessContext(args, shellContext)
        val exitCode = try {
            program.run(context)
        } catch (e: Exception) {
            context.writeLine(e.message ?: "Error de ejecución")
            1
        }

        processManager.finish(process.pid, exitCode, context.collectedOutput())
        return processManager.get(process.pid)!!
    }

    private fun resolveProgram(path: String, shellContext: ShellContext): VirtualProgram {
        val format = ProgramFormat.fromPath(path)
        val fileName = path.substringAfterLast('\\')

        if (format == ProgramFormat.BAT) {
            return BatchProgram(readTextOrThrow(path, shellContext), this, fileName)
        }

        if (format == ProgramFormat.COM || format == ProgramFormat.EXE || format == ProgramFormat.SYS) {
            if (!shellContext.drive.exists(path)) {
                throw RuntimeExecutionException.ProgramNotFoundException(path)
            }

            val content = readTextOrThrow(path, shellContext)
            if (declaresBatchType(content)) {
                return BatchProgram(stripBatchHeader(content), this, fileName)
            }
            return programRegistry.create(path)
        }

        throw RuntimeExecutionException.UnsupportedFormatException(path)
    }

    /** La primera línea del archivo dice, sin importar mayúsculas ni espacios, "FILETYPE=BAT". */
    private fun declaresBatchType(content: String): Boolean {
        val firstLine = content.lineSequence().firstOrNull() ?: return false
        val normalized = firstLine.replace(" ", "").uppercase()
        return normalized == "FILETYPE=BAT"
    }

    /** Quita la línea de declaración "FILETYPE=BAT", dejando solo los comandos reales. */
    private fun stripBatchHeader(content: String): String {
        val newlineIndex = content.indexOf('\n')
        return if (newlineIndex >= 0) content.substring(newlineIndex + 1) else ""
    }

    private fun readTextOrThrow(path: String, shellContext: ShellContext): String {
        val content = try {
            shellContext.drive.readFile(path)
        } catch (e: VirtualFileSystemException) {
            throw RuntimeExecutionException.ProgramNotFoundException(path)
        }
        return String(content)
    }
}
