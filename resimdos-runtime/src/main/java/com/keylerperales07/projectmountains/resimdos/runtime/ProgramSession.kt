package com.keylerperales07.projectmountains.resimdos.runtime

/**
 * Una ejecución de programa que puede quedar a medias esperando
 * teclado. La crea [ProgramLoader.launch]; la consola la mantiene
 * mientras [isWaitingForKey] sea verdadero y le va entregando las
 * líneas que escribe el usuario con [supplyLine].
 *
 * Un programa que no es reanudable (un .BAT, un programa registrado a
 * mano) se ejecuta de una vez dentro de la sesión y la sesión nace
 * terminada, así que la consola no necesita distinguir los dos casos.
 */
class ProgramSession internal constructor(
    private val processManager: ProcessManager,
    val process: VirtualProcess,
    private val program: VirtualProgram?,
    private val context: ProcessContext
) {

    var isWaitingForKey: Boolean = false
        private set

    /** Corre el primer tramo del programa; lo llama el loader al crear la sesión. */
    internal fun begin() {
        advance()
    }

    /** Líneas que el programa imprimió desde la última consulta. */
    fun takeOutput(): List<String> = context.takeNewLines()

    /** Lo que el programa dejó escrito sin salto de línea antes de esperar la tecla (puede ser vacío). */
    fun takePrompt(): String = context.takePrompt()

    /** El usuario envió una línea: se vuelve teclas en el búfer y el programa sigue desde donde quedó. */
    fun supplyLine(line: String) {
        if (!isWaitingForKey) return
        context.keyboard.pushLine(line)
        advance()
    }

    /** Termina el programa a la fuerza (Ctrl-C), con código de salida 1. */
    fun abort() {
        if (!isWaitingForKey) return
        isWaitingForKey = false
        processManager.finish(process.pid, exitCode = 1, output = context.collectedOutput())
    }

    private fun advance() {
        val current = program ?: return

        val step = try {
            if (current is ResumableProgram) current.advance(context) else ProgramStep.Finished(current.run(context))
        } catch (e: Exception) {
            context.writeLine(e.message ?: "Error de ejecución")
            ProgramStep.Finished(1)
        }

        when (step) {
            is ProgramStep.Finished -> {
                isWaitingForKey = false
                processManager.finish(process.pid, step.exitCode, context.collectedOutput())
            }
            ProgramStep.WaitingForKey -> isWaitingForKey = true
        }
    }
}
