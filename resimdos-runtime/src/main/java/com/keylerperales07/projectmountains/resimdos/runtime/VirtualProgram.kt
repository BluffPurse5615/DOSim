package com.keylerperales07.projectmountains.resimdos.runtime

/**
 * Un programa virtual ejecutable (.COM, .EXE o .SYS). El código real
 * lo aportará resimsdk cuando exista; por ahora esto es lo que un
 * programa creado a mano debe implementar y registrar en
 * [ProgramRegistry] para que el Program Loader lo encuentre.
 *
 * Devuelve el código de salida del proceso (0 = éxito, como en DOS).
 */
interface VirtualProgram {
    fun run(context: ProcessContext): Int
}
