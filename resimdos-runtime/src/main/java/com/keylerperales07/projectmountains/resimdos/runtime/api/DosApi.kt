package com.keylerperales07.projectmountains.resimdos.runtime.api

import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext
import com.keylerperales07.projectmountains.resimdos.runtime.ProgramLoader

/**
 * Punto de entrada único a las DOS APIs. Un [VirtualProgram] (de
 * resimdos-runtime) recibe una instancia de esto -normalmente
 * construida por resimsdk al empaquetar el programa- y a través de
 * ella toca archivos, directorios, consola, variables de entorno y
 * otros procesos, sin depender directamente de resimdos-fs ni
 * resimdos.runtime.shell.
 */
class DosApi(
    processContext: ProcessContext,
    programLoader: ProgramLoader
) {
    val files = FileApi(processContext)
    val directories = DirectoryApi(processContext)
    val console = ConsoleApi(processContext)
    val environment = EnvironmentApi(processContext)
    val process = ProcessApi(processContext, programLoader)
}
