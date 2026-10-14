package com.keylerperales07.projectmountains.resimdos.boot

import com.keylerperales07.projectmountains.resimdos.runtime.shell.ShellContext

/** Lo que devuelve [BootSequence.boot]: la sesión de shell lista, y lo que haya impreso AUTOEXEC.BAT si existía. */
data class BootResult(val shellContext: ShellContext, val autoexecOutput: List<String> = emptyList())
