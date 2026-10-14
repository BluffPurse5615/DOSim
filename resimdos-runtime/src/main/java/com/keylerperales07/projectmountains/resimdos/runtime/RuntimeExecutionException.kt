package com.keylerperales07.projectmountains.resimdos.runtime

sealed class RuntimeExecutionException(message: String) : Exception(message) {

    class ProgramNotFoundException(path: String) :
        RuntimeExecutionException("No se pudo encontrar el programa: $path")

    class UnsupportedFormatException(path: String) :
        RuntimeExecutionException("Formato de programa no soportado: $path")

    class NotRegisteredException(path: String) :
        RuntimeExecutionException("El programa no está registrado en el DOS Runtime: $path")
}
