package com.keylerperales07.projectmountains.resimdos.fs

/**
 * Errores propios de Resim-FS. Cada uno corresponde a una condición
 * de error real de DOS (equivalentes a los códigos de INT 21h), para
 * que resimdos-runtime pueda traducirlos a mensajes
 * como "File not found" o "Access denied".
 */
sealed class VirtualFileSystemException(message: String) : Exception(message) {

    class PathNotFoundException(path: String) :
        VirtualFileSystemException("Ruta no encontrada: $path")

    class AlreadyExistsException(path: String) :
        VirtualFileSystemException("Ya existe una entrada en: $path")

    class NotADirectoryException(path: String) :
        VirtualFileSystemException("No es un directorio: $path")

    class IsADirectoryException(path: String) :
        VirtualFileSystemException("Es un directorio, se esperaba un archivo: $path")

    class DirectoryNotEmptyException(path: String) :
        VirtualFileSystemException("El directorio no está vacío: $path")

    class AccessDeniedException(path: String) :
        VirtualFileSystemException("Acceso denegado (solo lectura): $path")

    class InvalidPathException(path: String) :
        VirtualFileSystemException("Ruta inválida: $path")
}
