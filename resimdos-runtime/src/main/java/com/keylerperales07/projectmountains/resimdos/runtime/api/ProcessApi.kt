package com.keylerperales07.projectmountains.resimdos.runtime.api

import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext
import com.keylerperales07.projectmountains.resimdos.runtime.ProgramLoader
import com.keylerperales07.projectmountains.resimdos.runtime.VirtualProcess

/**
 * Permite que un programa virtual lance otro (ej. un .BAT que corre
 * un .EXE, o un programa que encadena otro). Usa el mismo
 * [ProgramLoader] y la misma sesión de shell que lo lanzó a él.
 */
class ProcessApi(
    private val processContext: ProcessContext,
    private val programLoader: ProgramLoader
) {
    fun spawn(path: String, args: List<String> = emptyList()): VirtualProcess =
        programLoader.execute(path, args, processContext.shellContext)
}
