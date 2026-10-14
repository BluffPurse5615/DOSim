package com.keylerperales07.projectmountains.resimdos.runtime.api

import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileSystemException
import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext

/** Ejecuta [block], convirtiendo cualquier VirtualFileSystemException en un DosResult.Error. */
private fun <T> dosCall(block: () -> T): DosResult<T> = try {
    DosResult.Ok(block())
} catch (e: VirtualFileSystemException) {
    DosResult.Error(e.message ?: "Error de E/S")
}

/**
 * Operaciones sobre archivos para programas virtuales. Las rutas que
 * recibe pueden ser relativas o absolutas: se resuelven contra el
 * directorio actual de la sesión, igual que en COMMAND.COM.
 */
class FileApi(private val processContext: ProcessContext) {

    private val drive get() = processContext.shellContext.drive
    private fun resolve(path: String) = processContext.shellContext.resolvePath(path)

    fun exists(path: String): Boolean = drive.exists(resolve(path))

    fun read(path: String): DosResult<ByteArray> = dosCall { drive.readFile(resolve(path)) }

    fun readText(path: String): DosResult<String> = dosCall { String(drive.readFile(resolve(path))) }

    fun write(path: String, content: ByteArray, append: Boolean = false): DosResult<Unit> =
        dosCall { drive.writeFile(resolve(path), content, append) }

    fun writeText(path: String, content: String, append: Boolean = false): DosResult<Unit> =
        write(path, content.toByteArray(), append)

    fun create(path: String, content: ByteArray = ByteArray(0)): DosResult<Unit> =
        dosCall { drive.createFile(resolve(path), content); Unit }

    fun delete(path: String): DosResult<Unit> = dosCall { drive.deleteFile(resolve(path)); Unit }

    fun rename(path: String, newName: String): DosResult<Unit> =
        dosCall { drive.rename(resolve(path), newName); Unit }

    fun move(sourcePath: String, destinationPath: String): DosResult<Unit> =
        dosCall { drive.move(resolve(sourcePath), resolve(destinationPath)); Unit }

    fun getAttributes(path: String): DosResult<Int> = dosCall { drive.getAttributes(resolve(path)) }

    fun setAttributes(path: String, attributes: Int): DosResult<Unit> =
        dosCall { drive.setAttributes(resolve(path), attributes); Unit }
}
