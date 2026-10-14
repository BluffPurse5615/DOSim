package com.keylerperales07.projectmountains.resimdos.boot

import com.keylerperales07.projectmountains.resimdos.fs.VirtualDrive
import com.keylerperales07.projectmountains.resimdos.runtime.ProgramLoader
import com.keylerperales07.projectmountains.resimdos.runtime.ProgramRegistry
import com.keylerperales07.projectmountains.resimdos.runtime.shell.ShellContext

/**
 * Arranque de ReSim DOS 1.25. No hay CONFIG.SYS (eso es de versiones
 * posteriores de DOS), pero AUTOEXEC.BAT sí existe desde 1.25: el
 * COMMAND.ASM original busca \AUTOEXEC.BAT y, si existe, lo ejecuta
 * antes de mostrar el prompt. Aquí se hace lo mismo: si el archivo
 * no está, simplemente se sigue de largo.
 *
 * También se asegura de que COMMAND.COM y los comandos externos
 * existan como archivos reales en el disco (se crean la primera vez
 * si faltan; si el archivo ya existe en el disco real, no se toca).
 *
 * COMMAND.COM es texto plano, no un binario: empieza con la línea
 * "FILETYPE=BAT" y luego trae comandos reales de DOS 1.25 (DIR, etc.),
 * igual que un .BAT. El Program Loader lee esa primera línea y por
 * eso lo ejecuta línea por línea, sin importar que su extensión sea
 * .COM (ver ProgramLoader.resolveProgram).
 */
class BootSequence(
    private val drive: VirtualDrive,
    private val programRegistry: ProgramRegistry,
    private val programLoader: ProgramLoader
) {
    fun boot(): BootResult {
        ensureCommandComExists()
        ensureExternalCommandsExist()
        val shellContext = ShellContext(drive)
        val autoexecOutput = runAutoexecIfPresent(shellContext)
        return BootResult(shellContext, autoexecOutput)
    }

    private fun ensureCommandComExists() {
        val path = "\\COMMAND.COM"
        if (!drive.exists(path)) {
            drive.createFile(path, DEFAULT_COMMAND_COM.toByteArray())
        }
    }

    private fun ensureExternalCommandsExist() {
        EXTERNAL_COMMANDS.forEach { commandName ->
            val path = "\\$commandName.COM"
            if (!drive.exists(path)) {
                drive.createFile(path, ByteArray(0))
            }
            programRegistry.register(path) { StubExternalProgram(commandName) }
        }
    }

    /** Si \AUTOEXEC.BAT existe, se ejecuta ahora; si no, se sigue de largo sin error. */
    private fun runAutoexecIfPresent(shellContext: ShellContext): List<String> {
        val path = "\\AUTOEXEC.BAT"
        if (!drive.exists(path)) return emptyList()

        return try {
            val process = programLoader.execute(path, emptyList(), shellContext)
            process.output
        } catch (e: Exception) {
            listOf(e.message ?: "Error al ejecutar AUTOEXEC.BAT")
        }
    }

    companion object {
        private val EXTERNAL_COMMANDS = listOf(
            "FORMAT", "DISKCOPY", "DISKCOMP", "CHKDSK", "MODE", "SYS", "DEBUG", "LINK", "EXE2BIN"
        )

        /** Contenido real de COMMAND.COM: solo comandos que existen de verdad en DOS 1.25. */
        private const val DEFAULT_COMMAND_COM =
        "FILETYPE=BAT\n" +
        "@ECHO OFF\n" +
        "REM COMMAND.COM - interprete de comandos de ReSim DOS 1.25\n" +
        "DATE\n" +
        "TIME\n"
    }
}
