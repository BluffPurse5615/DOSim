package com.keylerperales07.projectmountains.resimdos.fs

import java.io.File

/**
 * Una unidad virtual (ej. "C:") respaldada por una carpeta real del
 * dispositivo. Cada archivo o directorio virtual es realmente un
 * archivo o directorio en el almacenamiento del dispositivo dentro
 * de [realRoot]; los atributos DOS que el sistema de archivos real
 * no soporta se guardan aparte mediante [AttributeStore].
 *
 * ReSimCore es quien crea y administra instancias de VirtualDrive
 * (montaje/desmontaje); resimdos-runtime solo debería
 * consumir esta clase a través de la unidad que ReSimCore les entregue.
 */
class VirtualDrive(
    val driveLetter: Char,
    private val realRoot: File
) {
    init {
        if (!realRoot.exists() && !realRoot.mkdirs()) {
            throw IllegalStateException("No se pudo crear la raíz real de la unidad $driveLetter: $realRoot")
        }
    }

    // ---------- Consultas ----------

    fun exists(path: String): Boolean = resolveOrNull(path) != null

    fun isDirectory(path: String): Boolean = resolveOrNull(path)?.isDirectory == true

    fun getEntry(path: String): VirtualFileEntry {
        val real = resolveOrThrow(path)
        return describeEntry(real)
    }

    fun listDirectory(path: String): List<VirtualFileEntry> {
        val real = resolveOrThrow(path)
        if (!real.isDirectory) throw VirtualFileSystemException.NotADirectoryException(path)
        val children = real.listFiles() ?: emptyArray()
        return children
        .filter { !AttributeStore.isReserved(it.name) }
        .map { describeEntry(it) }
        .sortedWith(compareByDescending<VirtualFileEntry> { it.isDirectory }.thenBy { it.name })
    }

    fun getAttributes(path: String): Int = getEntry(path).attributes

    fun setAttributes(path: String, attributes: Int) {
        val real = resolveOrThrow(path)
        attributeStoreFor(real.parentFile ?: realRoot).setAttributes(real.name, attributes)
    }

    // ---------- Directorios ----------

    fun createDirectory(path: String): VirtualFileEntry {
        val real = resolveTarget(path)
        if (real.exists()) throw VirtualFileSystemException.AlreadyExistsException(path)
        ensureParentExists(real, path)
        if (!real.mkdir()) throw VirtualFileSystemException.InvalidPathException(path)
        val now = System.currentTimeMillis()
        attributeStoreFor(real.parentFile!!).recordCreated(real.name, FileAttributes.DIRECTORY, now)
        return describeEntry(real)
    }

    fun deleteDirectory(path: String, recursive: Boolean = false) {
        val real = resolveOrThrow(path)
        if (!real.isDirectory) throw VirtualFileSystemException.NotADirectoryException(path)
        assertWritable(real, path)

        val children = real.listFiles()?.filterNot { AttributeStore.isReserved(it.name) } ?: emptyList()
        if (children.isNotEmpty() && !recursive) {
            throw VirtualFileSystemException.DirectoryNotEmptyException(path)
        }
        real.deleteRecursively()
        attributeStoreFor(real.parentFile ?: realRoot).remove(real.name)
    }

    // ---------- Archivos ----------

    fun createFile(path: String, content: ByteArray = ByteArray(0)): VirtualFileEntry {
        val real = resolveTarget(path)
        if (real.exists()) throw VirtualFileSystemException.AlreadyExistsException(path)
        ensureParentExists(real, path)
        real.writeBytes(content)
        val now = System.currentTimeMillis()
        attributeStoreFor(real.parentFile!!).recordCreated(real.name, FileAttributes.ARCHIVE, now)
        return describeEntry(real)
    }

    fun readFile(path: String): ByteArray {
        val real = resolveOrThrow(path)
        if (real.isDirectory) throw VirtualFileSystemException.IsADirectoryException(path)
        return real.readBytes()
    }

    fun writeFile(path: String, content: ByteArray, append: Boolean = false) {
        val real = resolveOrCreateForWrite(path)
        assertWritable(real, path)
        if (append) real.appendBytes(content) else real.writeBytes(content)
    }

    fun deleteFile(path: String) {
        val real = resolveOrThrow(path)
        if (real.isDirectory) throw VirtualFileSystemException.IsADirectoryException(path)
        assertWritable(real, path)
        if (!real.delete()) throw VirtualFileSystemException.InvalidPathException(path)
        attributeStoreFor(real.parentFile ?: realRoot).remove(real.name)
    }

    // ---------- Renombrar / mover ----------

    fun rename(path: String, newName: String) {
        val real = resolveOrThrow(path)
        assertWritable(real, path)
        val newRealName = VirtualPath.segments(VirtualPath.join(listOf(newName))).first()
        val target = File(real.parentFile, newRealName)
        if (target.exists()) throw VirtualFileSystemException.AlreadyExistsException(newName)
        if (!real.renameTo(target)) throw VirtualFileSystemException.InvalidPathException(path)
        attributeStoreFor(real.parentFile ?: realRoot).rename(real.name, newRealName)
    }

    fun move(sourcePath: String, destinationPath: String) {
        val source = resolveOrThrow(sourcePath)
        assertWritable(source, sourcePath)
        val destination = resolveTarget(destinationPath)
        if (destination.exists()) throw VirtualFileSystemException.AlreadyExistsException(destinationPath)
        ensureParentExists(destination, destinationPath)
        if (!source.renameTo(destination)) throw VirtualFileSystemException.InvalidPathException(sourcePath)

        val sourceAttrs = attributeStoreFor(source.parentFile ?: realRoot)
        val savedAttrs = sourceAttrs.getAttributes(source.name, defaultAttributesFor(destination))
        val savedCreated = sourceAttrs.getCreatedAt(source.name, System.currentTimeMillis())
        sourceAttrs.remove(source.name)
        attributeStoreFor(destination.parentFile!!).recordCreated(destination.name, savedAttrs, savedCreated)
    }

    // ---------- Internos ----------

    /** Espacio libre real en el almacenamiento que respalda esta unidad (para el pie de DIR). */
    fun freeSpaceBytes(): Long = realRoot.freeSpace

    private fun describeEntry(real: File): VirtualFileEntry {
        val store = attributeStoreFor(real.parentFile ?: realRoot)
        val defaultAttrs = defaultAttributesFor(real)
        val attrs = store.getAttributes(real.name, defaultAttrs)
        val createdAt = store.getCreatedAt(real.name, real.lastModified())
        return VirtualFileEntry(
            name = if (real == realRoot) VirtualPath.ROOT else real.name,
            isDirectory = real.isDirectory,
            attributes = attrs,
            sizeBytes = if (real.isDirectory) 0L else real.length(),
            createdAt = createdAt,
            modifiedAt = real.lastModified()
        )
    }

    private fun defaultAttributesFor(real: File): Int =
    if (real.isDirectory) FileAttributes.DIRECTORY else FileAttributes.ARCHIVE

    private fun attributeStoreFor(realDirectory: File): AttributeStore = AttributeStore(realDirectory)

    private fun assertWritable(real: File, path: String) {
        val store = attributeStoreFor(real.parentFile ?: realRoot)
        val attrs = store.getAttributes(real.name, defaultAttributesFor(real))
        if (FileAttributes.has(attrs, FileAttributes.READ_ONLY)) {
            throw VirtualFileSystemException.AccessDeniedException(path)
        }
    }

    private fun ensureParentExists(real: File, path: String) {
        val parent = real.parentFile ?: return
        if (!parent.exists()) throw VirtualFileSystemException.PathNotFoundException(VirtualPath.parentOf(path))
        if (!parent.isDirectory) throw VirtualFileSystemException.NotADirectoryException(VirtualPath.parentOf(path))
    }

    /** Resuelve una ruta a su [File] real sin exigir que exista (para crear). */
    private fun resolveTarget(path: String): File {
        var current = realRoot
        for (segment in VirtualPath.segments(path)) {
            current = File(current, segment)
        }
        return current
    }

    private fun resolveOrNull(path: String): File? {
        val target = resolveTarget(path)
        return if (target.exists()) target else null
    }

    private fun resolveOrThrow(path: String): File =
    resolveOrNull(path) ?: throw VirtualFileSystemException.PathNotFoundException(path)

    private fun resolveOrCreateForWrite(path: String): File {
        val existing = resolveOrNull(path)
        if (existing != null) {
            if (existing.isDirectory) throw VirtualFileSystemException.IsADirectoryException(path)
            return existing
        }
        val target = resolveTarget(path)
        ensureParentExists(target, path)
        target.createNewFile()
        attributeStoreFor(target.parentFile!!).recordCreated(
            target.name, FileAttributes.ARCHIVE, System.currentTimeMillis()
        )
        return target
    }
}
