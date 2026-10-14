package com.keylerperales07.projectmountains.resimdos.runtime.api

import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileEntry
import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileSystemException
import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext

private fun <T> dosCall(block: () -> T): DosResult<T> = try {
    DosResult.Ok(block())
} catch (e: VirtualFileSystemException) {
    DosResult.Error(e.message ?: "Error de E/S")
}

/** Operaciones sobre directorios para programas virtuales. */
class DirectoryApi(private val processContext: ProcessContext) {

    private val drive get() = processContext.shellContext.drive
    private fun resolve(path: String) = processContext.shellContext.resolvePath(path)

    /** Directorio actual de la sesión que lanzó el programa (ej. "\GAMES"). */
    fun currentDirectory(): String = processContext.shellContext.currentDirectory

    fun list(path: String = "."): DosResult<List<VirtualFileEntry>> {
        val target = if (path == ".") currentDirectory() else resolve(path)
        return dosCall { drive.listDirectory(target) }
    }

    fun create(path: String): DosResult<Unit> = dosCall { drive.createDirectory(resolve(path)); Unit }

    fun delete(path: String, recursive: Boolean = false): DosResult<Unit> =
        dosCall { drive.deleteDirectory(resolve(path), recursive); Unit }

    fun isDirectory(path: String): Boolean = drive.isDirectory(resolve(path))
}
