package com.keylerperales07.projectmountains.resimdos.runtime

/**
 * Registro en memoria de programas virtuales (.COM/.EXE/.SYS)
 * disponibles para ejecutar. Un programa puede registrar una
 * implementación real por su ruta completa (ej. "\FORMAT.COM"), pero
 * cualquier archivo .COM/.EXE que exista en el disco real debe poder
 * "correr" igual, aunque nadie lo haya registrado explícitamente —
 * en ese caso se usa [GenericProgram] como respaldo.
 *
 * Los .BAT no pasan por aquí: se interpretan directamente como texto.
 */
class ProgramRegistry {

    private val factories = mutableMapOf<String, () -> VirtualProgram>()

    fun register(path: String, factory: () -> VirtualProgram) {
        factories[path.uppercase()] = factory
    }

    fun unregister(path: String) {
        factories.remove(path.uppercase())
    }

    fun isRegistered(path: String): Boolean = factories.containsKey(path.uppercase())

    fun create(path: String): VirtualProgram {
        val factory = factories[path.uppercase()]
        return if (factory != null) factory() else GenericProgram(path)
    }
}

/**
 * Respaldo para cualquier .COM/.EXE que exista en el disco pero no
 * tenga una implementación real todavía. Sin un compilador real
 * dentro del simulador, no hay forma de ejecutar código de máquina
 * de verdad, así que esto solo confirma que el archivo se encontró y
 * "corrió".
 */
private class GenericProgram(private val path: String) : VirtualProgram {
    override fun run(context: ProcessContext): Int {
        val fileName = path.substringAfterLast('\\')
        context.writeLine("$fileName (todavía sin implementación real)")
        return 0
    }
}
