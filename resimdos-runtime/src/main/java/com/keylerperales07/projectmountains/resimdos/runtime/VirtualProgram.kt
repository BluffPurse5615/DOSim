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

/** Cómo quedó un [ResumableProgram] después de correr un tramo. */
sealed class ProgramStep {
    /** El programa terminó; [exitCode] es su código de salida. */
    class Finished(val exitCode: Int) : ProgramStep()

    /** Necesita una tecla que todavía no llegó; seguirá donde quedó cuando [ProcessContext.keyboard] tenga entrada. */
    object WaitingForKey : ProgramStep()
}

/**
 * Un programa que puede detenerse a esperar el teclado y continuar
 * después. [VirtualProgram.run] es síncrono y la consola no tiene
 * ningún canal por el que escribirle mientras corre, así que un
 * programa que lee teclas no puede quedarse dentro de run(): avanza
 * hasta que necesita una tecla, devuelve [ProgramStep.WaitingForKey],
 * y la consola vuelve a llamar a [advance] cuando el usuario envía
 * la siguiente línea.
 *
 * [run] sigue existiendo para quien ejecuta de forma síncrona (un
 * .BAT): allí nadie puede teclear, y el programa decide qué hacer.
 */
interface ResumableProgram : VirtualProgram {
    fun advance(context: ProcessContext): ProgramStep
}
