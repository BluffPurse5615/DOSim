package com.keylerperales07.projectmountains.resimdos.runtime.shell

import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileEntry
import com.keylerperales07.projectmountains.resimdos.fs.VirtualPath

/*
 * Comandos internos de COMMAND.COM para DOS 1.25: DIR, COPY, DEL/ERASE,
 * REN/RENAME, TYPE, DATE, TIME, REM, PAUSE. No hay subdirectorios, así
 * que toda ruta es la raíz de la unidad (ver parseFileSpec).
 */

private const val CTRL_Z: Byte = 0x1A

/** Archivos (no carpetas) de la raíz que encajan con [pattern]. */
private fun filesMatching(pattern: String, context: ShellContext): List<VirtualFileEntry> =
    context.drive.listDirectory(VirtualPath.ROOT)
        .filter { !it.isDirectory && Wildcard.matches(pattern, it.name) }

private fun requireOneFile(files: List<String>): String {
    if (files.isEmpty()) throw UsageException("Falta el nombre del archivo")
    if (files.size > 1) throw UsageException("Número de parámetros no válido")
    return files[0]
}

class DirCommand : Command {
    override val name = "DIR"
    override val syntax = "DIR [unidad:][ruta][nombre_archivo] [/P] [/W]"

    // /W en DOS reparte 5 nombres por fila; con la consola a 62 columnas
    // solo caben 4 de 12 caracteres separados por 2 espacios.
    private val wideColumns = 4

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        val (switches, files) = splitSwitches(args, setOf('P', 'W'))
        if (files.size > 1) throw UsageException("Número de parámetros no válido")

        val pattern = files.firstOrNull()?.let { parseFileSpec(it, context).pattern }.orEmpty().ifEmpty { "*.*" }
        val entries = context.drive.listDirectory(VirtualPath.ROOT)
            .filter { Wildcard.matches(pattern, it.name) }
        // En un disco vacío "DIR" sí muestra el encabezado; solo falla cuando pidieron algo concreto.
        if (entries.isEmpty() && pattern != "*.*") return CommandResult.error("Archivo no encontrado")

        val lines = mutableListOf(
            " Volume in drive ${context.drive.driveLetter} has no label",
            " Directory of  ${context.drive.driveLetter}:\\",
            ""
        )
        if ('W' in switches) {
            entries.chunked(wideColumns).forEach { row ->
                lines.add(row.joinToString("  ") { wideName(it) }.trimEnd())
            }
        } else {
            entries.forEach { lines.add(formatEntry(it)) }
        }
        lines.add("")
        lines.add("     ${entries.count { !it.isDirectory }} File(s)   ${context.drive.freeSpaceBytes()} bytes free")

        return if ('P' in switches) {
            CommandResult.paged(lines, (context.screenRows - 2).coerceAtLeast(1))
        } else {
            CommandResult(output = lines)
        }
    }

    private fun splitName(entry: VirtualFileEntry): Pair<String, String> {
        val dot = entry.name.lastIndexOf('.')
        return if (dot >= 0) entry.name.substring(0, dot) to entry.name.substring(dot + 1) else entry.name to ""
    }

    private fun wideName(entry: VirtualFileEntry): String {
        val (base, ext) = splitName(entry)
        return "${base.padEnd(8)} ${ext.padEnd(3)}"
    }

    private fun formatEntry(entry: VirtualFileEntry): String {
        val (base, ext) = splitName(entry)
        val size = if (entry.isDirectory) "<DIR>" else entry.sizeBytes.toString()
        return "${base.padEnd(8)} ${ext.padEnd(3)}   ${size.padStart(8)}   " +
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
    override val syntax = "DEL [unidad:][ruta]nombre_archivo"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        val (_, files) = splitSwitches(args, emptySet())
        val spec = parseFileSpec(requireOneFile(files), context)
        if (spec.pattern.isEmpty()) throw UsageException("Falta el nombre del archivo")

        val matches = filesMatching(spec.pattern, context)
        if (matches.isEmpty()) return CommandResult.error("Archivo no encontrado")
        matches.forEach { context.drive.deleteFile(context.resolvePath(it.name)) }
        return CommandResult.ok()
    }
}

/** Alias de DEL. */
class EraseCommand : Command {
    override val name = "ERASE"
    override val syntax = "ERASE [unidad:][ruta]nombre_archivo"
    private val delegate = DelCommand()
    override fun execute(args: List<String>, context: ShellContext): CommandResult = delegate.execute(args, context)
}

class RenCommand : Command {
    override val name = "REN"
    override val syntax = "REN [unidad:][ruta]nombre_actual nombre_nuevo"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        val (_, files) = splitSwitches(args, emptySet())
        if (files.size != 2) throw UsageException("Número de parámetros no válido")

        val spec = parseFileSpec(files[0], context)
        if (spec.pattern.isEmpty()) throw UsageException("Falta el nombre del archivo")
        val newPattern = files[1]
        if (newPattern.any { it == ':' || it == '\\' }) {
            throw UsageException("El nombre nuevo no lleva unidad ni ruta")
        }

        val matches = filesMatching(spec.pattern, context)
        if (matches.isEmpty()) return CommandResult.error("Archivo no encontrado")

        // Se calculan y revisan todos los nombres antes de renombrar el primero,
        // para no dejar el disco a medias si el tercero choca con otro archivo.
        val renames = matches.map { it to Wildcard.apply(newPattern, it.name) }
        val newNames = renames.map { it.second }
        if (newNames.any { it.isEmpty() }) throw UsageException("Nombre nuevo no válido")
        val clash = newNames.firstOrNull { context.drive.exists(context.resolvePath(it)) }
            ?: newNames.groupBy { it }.entries.firstOrNull { it.value.size > 1 }?.key
        if (clash != null) {
            throw UsageException("Nombre de archivo duplicado o archivo no encontrado: $clash", showSyntax = false)
        }

        renames.forEach { (entry, newName) -> context.drive.rename(context.resolvePath(entry.name), newName) }
        return CommandResult.ok()
    }
}

/** Alias de REN. */
class RenameCommand : Command {
    override val name = "RENAME"
    override val syntax = "RENAME [unidad:][ruta]nombre_actual nombre_nuevo"
    private val delegate = RenCommand()
    override fun execute(args: List<String>, context: ShellContext): CommandResult = delegate.execute(args, context)
}

class TypeCommand : Command {
    override val name = "TYPE"
    override val syntax = "TYPE [unidad:][ruta]nombre_archivo"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        val (_, files) = splitSwitches(args, emptySet())
        val spec = parseFileSpec(requireOneFile(files), context)
        if (spec.pattern.isEmpty() || Wildcard.hasWildcard(spec.pattern)) {
            throw UsageException("Nombre de archivo no válido")
        }
        var content = context.drive.readFile(context.resolvePath(spec.pattern))
        // TYPE se detiene en el primer Ctrl+Z, igual que el modo ASCII de COPY.
        val end = content.indexOf(CTRL_Z)
        if (end >= 0) content = content.copyOf(end)
        return CommandResult(output = String(content).split("\n"))
    }
}

private enum class CopyMode { ASCII, BINARY }

private class CopyFile(val text: String, var mode: CopyMode? = null)

private class CopyRequest(
    val sources: List<CopyFile>,
    val destination: CopyFile?,
    val globalMode: CopyMode?,
    val verify: Boolean
)

/**
 * COPY [/A | /B] origen [/A | /B] [destino] [/V], con "+" para concatenar.
 * Un /A o /B antes del primer archivo vale para todos; después de un
 * archivo vale solo para ese. En ASCII el origen se lee hasta el primer
 * Ctrl+Z y al destino se le agrega uno al final; en binario no se toca
 * nada. Sin modificadores, concatenar es ASCII y copiar es binario.
 * Los modificadores van separados por espacios.
 */
class CopyCommand : Command {
    override val name = "COPY"
    override val syntax = "COPY [/A | /B] origen [/A | /B] [destino] [/V]"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        val request = parse(args)
        val sourcePatterns = request.sources.map { parseFileSpec(it.text, context).pattern }
        if (sourcePatterns.any { it.isEmpty() }) throw UsageException("Falta el nombre del archivo")

        val destinationPattern = request.destination
            ?.let { parseFileSpec(it.text, context).pattern }
            ?.ifEmpty { null }

        // Cada origen (con sus comodines expandidos) como lista de [archivo, modo].
        val concatenating = request.sources.size > 1 ||
                (destinationPattern != null && !Wildcard.hasWildcard(destinationPattern) &&
                        Wildcard.hasWildcard(sourcePatterns[0]))
        val concatDefault = if (concatenating) CopyMode.ASCII else CopyMode.BINARY
        fun modeOf(file: CopyFile?) = file?.mode ?: request.globalMode ?: concatDefault

        val inputs = request.sources.indices.flatMap { index ->
            val found = filesMatching(sourcePatterns[index], context)
            if (found.isEmpty()) return CommandResult.error("Archivo no encontrado")
            found.map { it.name to modeOf(request.sources[index]) }
        }
        val destinationMode = modeOf(request.destination)

        val copied = if (concatenating) {
            if (destinationPattern != null && Wildcard.hasWildcard(destinationPattern)) {
                throw UsageException("Nombre de archivo no válido")
            }
            val data = inputs.fold(ByteArray(0)) { acc, (name, mode) -> acc + readSource(context, name, mode) }
            write(context, destinationPattern ?: inputs.first().first, data, destinationMode, request.verify)
            1
        } else {
            // Se valida todo antes de escribir: ningún archivo puede caer sobre sí mismo.
            val targets = inputs.map { (name, mode) ->
                val target = if (destinationPattern == null) name else Wildcard.apply(destinationPattern, name)
                if (target.equals(name, ignoreCase = true)) {
                    return CommandResult.error("El archivo no se puede copiar sobre sí mismo")
                }
                Triple(name, mode, target)
            }
            targets.forEach { (name, mode, target) ->
                write(context, target, readSource(context, name, mode), destinationMode, request.verify)
            }
            targets.size
        }
        return CommandResult.ok("%9d archivo(s) copiado(s)".format(copied))
    }

    private fun parse(args: List<String>): CopyRequest {
        val files = mutableListOf<CopyFile>()
        var sourceCount = 0
        var globalMode: CopyMode? = null
        var verify = false
        var afterPlus = false
        var destinationSeen = false

        fun addName(text: String) {
            when {
                destinationSeen -> throw UsageException("Número de parámetros no válido")
                afterPlus || files.isEmpty() -> {
                    files.add(CopyFile(text))
                    sourceCount++
                    afterPlus = false
                }
                else -> {
                    files.add(CopyFile(text))
                    destinationSeen = true
                }
            }
        }

        fun addPlus() {
            if (destinationSeen || files.isEmpty() || afterPlus) throw UsageException("Número de parámetros no válido")
            afterPlus = true
        }

        for (arg in args) {
            if (arg.startsWith("/")) {
                val mode = when (arg.drop(1).singleOrNull()?.uppercaseChar()) {
                    'V' -> { verify = true; continue }
                    'A' -> CopyMode.ASCII
                    'B' -> CopyMode.BINARY
                    else -> throw UsageException("Modificador no válido - $arg")
                }
                if (files.isEmpty()) globalMode = mode else files.last().mode = mode
                continue
            }
            // Un mismo argumento puede traer "+" pegado: "UNO.TXT+DOS.TXT", "UNO.TXT+", "+DOS.TXT".
            arg.split('+').forEachIndexed { index, piece ->
                if (index > 0) addPlus()
                if (piece.isNotEmpty()) addName(piece)
            }
        }

        if (afterPlus) throw UsageException("Número de parámetros no válido")
        if (sourceCount == 0) throw UsageException("Falta el nombre del archivo")
        return CopyRequest(files.take(sourceCount), files.getOrNull(sourceCount), globalMode, verify)
    }

    private fun readSource(context: ShellContext, name: String, mode: CopyMode): ByteArray {
        val bytes = context.drive.readFile(context.resolvePath(name))
        if (mode == CopyMode.BINARY) return bytes
        val end = bytes.indexOf(CTRL_Z)
        return if (end < 0) bytes else bytes.copyOf(end)
    }

    private fun write(context: ShellContext, name: String, data: ByteArray, mode: CopyMode, verify: Boolean) {
        val content = if (mode == CopyMode.ASCII) data + CTRL_Z else data
        val path = context.resolvePath(name)
        if (context.drive.exists(path)) {
            context.drive.writeFile(path, content)
        } else {
            context.drive.createFile(path, content)
        }
        if (verify && !context.drive.readFile(path).contentEquals(content)) {
            throw UsageException("Error de verificación al escribir $name", showSyntax = false)
        }
    }
}

class DateCommand : Command {
    override val name = "DATE"
    override val syntax = "DATE [mm-dd-yy]"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.awaitingClock("DATE", "Current date is ${context.clock.dateText}", "Enter new date: ")
        }
        val value = args.joinToString(" ")
        if (!DosClockFormat.isValidDate(value)) throw UsageException("Fecha inválida")
        context.clock.dateText = value
        return CommandResult.ok()
    }
}

class TimeCommand : Command {
    override val name = "TIME"
    override val syntax = "TIME [hh:mm[:ss[.cc]]]"

    override fun execute(args: List<String>, context: ShellContext): CommandResult {
        if (args.isEmpty()) {
            return CommandResult.awaitingClock("TIME", "Current time is ${context.clock.timeText}", "Enter new time: ")
        }
        val value = args.joinToString(" ")
        if (!DosClockFormat.isValidTime(value)) throw UsageException("Hora inválida")
        context.clock.timeText = value
        return CommandResult.ok()
    }
}

/** No hace nada: sirve para dejar comentarios dentro de archivos .BAT. */
class RemCommand : Command {
    override val name = "REM"
    override val syntax = "REM texto_del_comentario"
    override fun execute(args: List<String>, context: ShellContext): CommandResult = CommandResult.ok()
}

/** Espera "cualquier tecla" (en nuestro caso, cualquier línea) antes de seguir. */
class PauseCommand : Command {
    override val name = "PAUSE"
    override val syntax = "PAUSE"
    override fun execute(args: List<String>, context: ShellContext): CommandResult =
        CommandResult.pause(CommandResult.PRESS_KEY_PROMPT)
}