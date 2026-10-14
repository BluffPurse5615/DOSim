package com.keylerperales07.projectmountains.resimdos.boot

import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext
import com.keylerperales07.projectmountains.resimdos.runtime.VirtualProgram

/**
 * FORMAT, CHKDSK, DEBUG, etc. existen como archivos reales en el
 * disco (son comandos "externos"), pero todavía no tienen una
 * implementación real. Por ahora solo confirman que se encontraron y
 * se "ejecutaron", como marcador de que falta programarlos de verdad.
 */
internal class StubExternalProgram(private val commandName: String) : VirtualProgram {
    override fun run(context: ProcessContext): Int {
        context.writeLine("$commandName: comando externo (todavía no implementado)")
        return 0
    }
}
