package com.keylerperales07.projectmountains.resimdos.runtime.shell

import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileEntry
import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileSystemException
import com.keylerperales07.projectmountains.resimdos.fs.VirtualPath

/**
 * Comandos de COMMAND.COM para DOS 1.25: sin subdirectorios (no hay
 * CD/MD/RD), sin PATH/SET/ECHO/CLS/VER/VOL/MOVE (llegaron en versiones
 * posteriores de DOS). Los únicos internos son: DIR, COPY, DEL/ERASE,
 * REN/RENAME, TYPE, DATE, TIME, REM, PAUSE.
 */
private fun VirtualFileSystemException.toCommandResult(): CommandResult =
CommandResult.error(message ?: "Error")

class DirCommand : Command {
    override val name = "DIR"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        return try {
            val entries = context.drive.listDirectory(VirtualPath.ROOT)
            val lines = mutableListOf(
                " Volume in drive ${context.drive.driveLetter} has no label",
                " Directory of  ${context.drive.driveLetter}:\\",
                ""
            )
            entries.forEach { entry -> lines.add(formatEntry(entry)) }
            lines.add("")
            val fileCount = entries.count { !it.isDirectory }
            lines.add("     $fileCount File(s)   ${context.drive.freeSpaceBytes()} bytes free")
            CommandResult.ok(*lines.toTypedArray())
        } catch (e: VirtualFileSystemException) {
            e.toCommandResult()
        }
    }

    private fun formatEntry(entry: VirtualFileEntry): String {
        val dotIndex = entry.name.lastIndexOf('.')
        val baseName = if (dotIndex >= 0) entry.name.substring(0, dotIndex) else entry.name
        val extension = if (dotIndex >= 0) entry.name.substring(dotIndex + 1) else ""
        val size = if (entry.isDirectory) "<DIR>" else entry.sizeBytes.toString()
        return "${baseName.padEnd(8)} ${extension.padEnd(3)}   ${size.padStart(8)}   " +
        "${formatDate(entry.modifiedAt)}   ${formatTime(entry.modifiedAt)}"
    }

    private fun formatDate(epochMillis: Long): String {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val year = calendar.get(java.util.Calendar.YEAR) % 100
        return "%d-%02d-%02d".format(month, day, year)
    }

    private fun formatTime(epochMillis: Long): String {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
        var hour = calendar.get(java.util.Calendar.HOUR)
        if (hour == 0) hour = 12
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val amPm = if (calendar.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM) "a" else "p"
        return "%d:%02d%s".format(hour, minute, amPm)
    }
}

class DelCommand : Command {
    override val name = "DEL"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        if (args.isEmpty()) return CommandResult.error("Falta el nombre del archivo")
        val target = context.resolvePath(args[0])
        return try {
            context.drive.deleteFile(target)
            CommandResult.ok()
        } catch (e: VirtualFileSystemException) {
            e.toCommandResult()
        }
    }
}

/** Alias de DEL. */
class EraseCommand : Command {
    override val name = "ERASE"
    private val delegate = DelCommand()
    override fun execute(args: List<String>, context: ShellContext): CommandResult = delegate.execute(args, context)
}

class RenCommand : Command {
    override val name = "REN"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        if (args.size < 2) return CommandResult.error("Sintaxis: REN <origen> <nuevo_nombre>")
        val target = context.resolvePath(args[0])
        return try {
            context.drive.rename(target, args[1])
            CommandResult.ok()
        } catch (e: VirtualFileSystemException) {
            e.toCommandResult()
        }
    }
}

/** Alias de REN. */
class RenameCommand : Command {
    override val name = "RENAME"
    private val delegate = RenCommand()
    override fun execute(args: List<String>, context: ShellContext): CommandResult = delegate.execute(args, context)
}

class TypeCommand : Command {
    override val name = "TYPE"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        if (args.isEmpty()) return CommandResult.error("Falta el nombre del archivo")
        val target = context.resolvePath(args[0])
        return try {
            val content = context.drive.readFile(target)
            CommandResult(output = String(content).split("\n"))
        } catch (e: VirtualFileSystemException) {
            e.toCommandResult()
        }
    }
}

class CopyCommand : Command {
    override val name = "COPY"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        if (args.size < 2) return CommandResult.error("Sintaxis: COPY <origen> <destino>")
        val source = context.resolvePath(args[0])
        val destination = context.resolvePath(args[1])
        if (source == destination) return CommandResult.error("El archivo no se puede copiar sobre sí mismo")
        return try {
            val content = context.drive.readFile(source)
            if (context.drive.exists(destination)) {
                context.drive.writeFile(destination, content)
            } else {
                context.drive.createFile(destination, content)
            }
            CommandResult.ok("        1 archivo(s) copiado(s)")
        } catch (e: VirtualFileSystemException) {
            e.toCommandResult()
        }
    }
}

class DateCommand : Command {
    override val name = "DATE"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.awaitingClock("DATE", "Current date is ${context.clock.dateText}", "Enter new date: ")
        }
        val value = args.joinToString(" ")
        if (!DosClockFormat.isValidDate(value)) return CommandResult.error("Fecha inválida")
        context.clock.dateText = value
        return CommandResult.ok()
    }
}

class TimeCommand : Command {
    override val name = "TIME"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.awaitingClock("TIME", "Current time is ${context.clock.timeText}", "Enter new time: ")
        }
        val value = args.joinToString(" ")
        if (!DosClockFormat.isValidTime(value)) return CommandResult.error("Hora inválida")
        context.clock.timeText = value
        return CommandResult.ok()
    }
}

/** No hace nada: sirve para dejar comentarios dentro de archivos .BAT. */
class RemCommand : Command {
    override val name = "REM"
    override fun execute(args: List<String>, context: ShellContext): CommandResult = CommandResult.ok()
}

/** Espera "cualquier tecla" (en nuestro caso, cualquier línea) antes de seguir. */
class PauseCommand : Command {
    override val name = "PAUSE"
    override fun execute(args: List<String>, context: ShellContext): CommandResult =
    CommandResult.pause("Presione una tecla para continuar . . .")
}
