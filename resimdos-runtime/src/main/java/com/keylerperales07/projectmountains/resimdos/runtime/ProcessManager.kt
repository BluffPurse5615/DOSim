package com.keylerperales07.projectmountains.resimdos.runtime

/**
 * Lleva el registro de procesos virtuales lanzados (pasados y en
 * curso). La ejecución en sí es síncrona por ahora (no hay
 * concurrencia real todavía), pero cada ejecución igual se registra
 * como un proceso con su propio PID, para que el shell pueda
 * consultarlos (ej. un futuro comando "TASKLIST" o "PS").
 */
class ProcessManager {

    private var nextPid = 1
    private val processes = mutableMapOf<Int, VirtualProcess>()

    internal fun start(programPath: String): VirtualProcess {
        val process = VirtualProcess(pid = nextPid++, programPath = programPath)
        processes[process.pid] = process
        return process
    }

    internal fun finish(pid: Int, exitCode: Int, output: List<String>) {
        processes[pid]?.let {
            it.state = if (exitCode == 0) ProcessState.FINISHED else ProcessState.FAILED
            it.exitCode = exitCode
            it.output = output
        }
    }

    fun get(pid: Int): VirtualProcess? = processes[pid]

    fun all(): List<VirtualProcess> = processes.values.sortedBy { it.pid }

    fun running(): List<VirtualProcess> = processes.values.filter { it.state == ProcessState.RUNNING }
}
