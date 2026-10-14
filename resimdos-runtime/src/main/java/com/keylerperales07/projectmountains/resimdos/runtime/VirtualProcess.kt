package com.keylerperales07.projectmountains.resimdos.runtime

enum class ProcessState { RUNNING, FINISHED, FAILED }

data class VirtualProcess(
    val pid: Int,
    val programPath: String,
    var state: ProcessState = ProcessState.RUNNING,
    var exitCode: Int? = null,
    var output: List<String> = emptyList()
)
