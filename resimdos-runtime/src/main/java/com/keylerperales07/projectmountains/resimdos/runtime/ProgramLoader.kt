package com.keylerperales07.projectmountains.resimdos.runtime

import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileSystemException
import com.keylerperales07.projectmountains.resimdos.runtime.asm.AsmProgram
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
 *   Si dice "FILETYPE=ASM", el texto es un programa en lenguaje
 *   ensamblador y lo corre [AsmProgram]; la extensión decide el
 *   modelo de memoria (.COM o .EXE).
 *   Si no trae ninguna declaración, se busca en [ProgramRegistry]; si
 *   no está registrado, se usa un programa genérico de respaldo (ver
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

    /**
     * Como [execute], pero el programa puede detenerse a esperar el
     * teclado: devuelve una [ProgramSession] que, si queda esperando
     * ([ProgramSession.isWaitingForKey]), la consola retoma con cada
     * línea que escribe el usuario. La usa la consola; los .BAT siguen
     * llamando a [execute] porque nadie puede teclear dentro de un lote.
     */
    fun launch(path: String, args: List<String>, shellContext: ShellContext): ProgramSession {
        val process = processManager.start(path)
        val context = ProcessContext(args, shellContext)

        val program = try {
            resolveProgram(path, shellContext)
        } catch (e: Exception) {
            context.writeLine(e.message ?: "Error")
            processManager.finish(process.pid, exitCode = 1, output = context.collectedOutput())
            return ProgramSession(processManager, process, null, context)
        }

        val session = ProgramSession(processManager, process, program, context)
        session.begin()
        return session
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
            when (declaredType(content)) {
                DeclaredType.BAT -> return BatchProgram(stripBatchHeader(content), this, fileName)
                // El texto ASM se entrega completo, con su línea de cabecera: así los
                // errores del parser citan el mismo número de línea que ve el usuario.
                DeclaredType.ASM -> return AsmProgram(content, format)
                null -> return programRegistry.create(path)
            }
        }

        throw RuntimeExecutionException.UnsupportedFormatException(path)
    }

    /** Lo que el archivo declara en su primera línea con FILETYPE=. */
    private enum class DeclaredType { BAT, ASM }

    /** Lee la primera línea (sin importar mayúsculas ni espacios) y dice qué tipo declara, o null si no declara ninguno. */
    private fun declaredType(content: String): DeclaredType? {
        val firstLine = content.lineSequence().firstOrNull() ?: return null
        return when (firstLine.replace(" ", "").uppercase()) {
            "FILETYPE=BAT" -> DeclaredType.BAT
            "FILETYPE=ASM" -> DeclaredType.ASM
            else -> null
        }
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
