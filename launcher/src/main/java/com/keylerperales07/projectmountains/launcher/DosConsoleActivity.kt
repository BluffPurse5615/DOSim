package com.keylerperales07.projectmountains.launcher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.util.TypedValue
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.keylerperales07.projectmountains.resimcore.ReSimCore
import com.keylerperales07.projectmountains.resimdos.boot.BootSequence
import com.keylerperales07.projectmountains.resimdos.fs.VirtualFileSystemException
import com.keylerperales07.projectmountains.resimdos.runtime.EnvironmentManager
import com.keylerperales07.projectmountains.resimdos.runtime.ProcessManager
import com.keylerperales07.projectmountains.resimdos.runtime.ProgramLoader
import com.keylerperales07.projectmountains.resimdos.runtime.ProgramRegistry
import com.keylerperales07.projectmountains.resimdos.runtime.RuntimeExecutionException
import com.keylerperales07.projectmountains.resimdos.runtime.shell.CommandInterpreter
import com.keylerperales07.projectmountains.resimdos.runtime.shell.DosClockFormat
import com.keylerperales07.projectmountains.resimdos.runtime.shell.ShellContext
import java.util.Calendar

/**
 * Consola de ReSim DOS 1.25 como una sola caja de texto continua (sin
 * campo de entrada separado abajo): la fecha/hora se escriben solas,
 * con animacion letra por letra, usando la fecha/hora real del
 * dispositivo, y despues el usuario escribe pegado al prompt "A>",
 * como una terminal real.
 */
class DosConsoleActivity : Activity() {

    private lateinit var consoleView: EditText
    private lateinit var shellContext: ShellContext
    private lateinit var interpreter: CommandInterpreter
    private lateinit var programLoader: ProgramLoader

    private val handler = Handler(Looper.getMainLooper())

    /** Posicion a partir de la cual el texto es "editable" (lo que va despues del 煤ltimo prompt). */
    private var promptStart = 0
    /** Copia exacta de todo el texto hasta promptStart; si algo la corrompe, se restaura. */
    private var lockedText = ""
    private var isReady = false
    /** Si DATE o TIME estan esperando que la siguiente linea sea el valor nuevo. */
    private var pendingClockField: String? = null
    /** Si PAUSE está esperando que el usuario mande cualquier línea para continuar. */
    private var pendingKeyPress = false
    /** Evita que nuestros propios appends disparen el detector de "Enter" del usuario. */
    private var guard = false

    private val dosTypeface: Typeface by lazy {
        try {
            Typeface.createFromAsset(assets, "fonts/dos-vga.ttf")
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }
    }

    /**
     * Fija la densidad de pantalla en un valor constante (DENSITY_DEFAULT,
     * 160dpi = "1x") para toda esta Activity, antes de que se cree
     * nada. Así, un dp o un sp del layout siempre se traduce al MISMO
     * número de píxeles reales sin importar el DPI real del teléfono
     * — la interfaz deja de "crecer" o "encogerse" entre dispositivos
     * de distinta densidad.
     */
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT
        val fixedDensityContext = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(fixedDensityContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setupUi()
        enableFullscreen()
        bootDos()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // El sistema puede volver a mostrar las barras (ej. al abrir el
        // teclado); en cuanto la ventana recupera el foco, se ocultan de nuevo.
        if (hasFocus) enableFullscreen()
    }

    /** Oculta la barra de estado y la de navegación, para pantalla completa real (útil para el tráiler). */
    private fun enableFullscreen() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } catch (e: Exception) {
                // Pantalla completa es cosmético: si el fabricante rompe esta API, no debe tumbar la app.
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    /**
     * OJO: 320x200 es la resolución GRÁFICA de CGA, cuyo modo de texto
     * nativo es de 40 columnas — pero la imagen de referencia de DOS
     * 5.00 que se usó para comparar es el modo de texto clásico de
     * 80 columnas (el que todo el mundo asocia con "la pantalla de
     * DOS"), no el de 40 de CGA gráfico. Por eso se apunta a 80 acá:
     * es la densidad que de verdad coincide con esa referencia visual,
     * aunque el lienzo siga siendo 320x200 por fuera.
     *
     * Como AspectRatioFrameLayout ya fuerza que la consola se MIDA en
     * exactamente 320 píxeles reales de ancho (ver
     * AspectRatioFrameLayout.kt), calculamos el tamaño de fuente que
     * hace que cada letra ocupe justo 320/80 = 4 píxeles — en vez de
     * adivinar un valor de sp fijo, que dependería de la fuente y se
     * vería distinto si algún día cambiamos dosTypeface.
     */
    private fun computeConsoleTextSizePx(targetColumns: Int = 62): Float {
        val virtualCanvasWidthPx = 320f
        val targetCharWidthPx = virtualCanvasWidthPx / targetColumns

        val probeSize = 100f
        val paint = Paint().apply {
            typeface = dosTypeface
            textSize = probeSize
        }
        // Fuente monoespaciada: cualquier letra sirve de referencia de ancho.
        val measuredCharWidthAtProbe = paint.measureText("M")

        return probeSize * (targetCharWidthPx / measuredCharWidthAtProbe)
    }

    private fun setupUi() {
        setContentView(R.layout.activity_main)
        consoleView = findViewById(R.id.consoleView)
        consoleView.typeface = dosTypeface
        consoleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, computeConsoleTextSizePx())
        // Antes de arrancar no se puede escribir: evita que el usuario
        // teclee encima de la animación de fecha/hora.
        consoleView.isFocusable = false
        consoleView.isFocusableInTouchMode = false

        consoleView.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable) {
                    if (!isReady || guard) return

                    if (s.length < promptStart || !s.toString().startsWith(lockedText)) {
                        // Se borró o modificó el historial/el prompt: se restaura tal como estaba.
                        guard = true
                        s.replace(0, s.length, lockedText)
                        guard = false
                        scrollToBottom()
                        return
                    }

                    val editableRegion = s.substring(promptStart)
                    val newlineIndex = editableRegion.indexOf('\n')
                    if (newlineIndex >= 0) {
                        val typed = editableRegion.substring(0, newlineIndex)
                        guard = true
                        s.delete(promptStart + newlineIndex, s.length)
                        guard = false
                        handleSubmittedLine(typed)
                    }
                }
        })
    }

    private fun bootDos() {
        val core = ReSimCore()
        val drive = ReSimCore.mountDefaultDosDrive(core, applicationContext)

        val processManager = ProcessManager()
        val programRegistry = ProgramRegistry()
        programLoader = ProgramLoader(processManager, programRegistry)

        val bootResult = BootSequence(drive, programRegistry, programLoader).boot()
        shellContext = bootResult.shellContext
        interpreter = CommandInterpreter(shellContext)

        appendLine("MS-DOS version 1.25")
        appendLine("Copyright 1981,82 Microsoft, Inc")
        appendLine("")
        appendLine("Command v. 1.17")
        appendLine("Current date is ${shellContext.clock.dateText}")
        appendLine("Enter new date: ")

        val realDate = realDateText()
        typeText(realDate) {
            shellContext.clock.dateText = realDate
            appendLine("Current time is ${shellContext.clock.timeText}")
            appendLine("Enter new time: ")

            val realTime = realTimeText()
            typeText(realTime) {
                shellContext.clock.timeText = realTime
                appendLine("")
                bootResult.autoexecOutput.forEach { appendLine(it) }
                finishBoot()
            }
        }
    }

    private fun finishBoot() {
        isReady = true
        printPrompt()
        consoleView.isFocusable = true
        consoleView.isFocusableInTouchMode = true
        consoleView.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(consoleView, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Escribe [text] letra por letra, como si alguien la estuviera tecleando, y llama a [onComplete] al terminar. */
    private fun typeText(text: String, delayMs: Long = 45L, onComplete: () -> Unit) {
        var index = 0
        val runnable = object : Runnable {
            override fun run() {
                if (index < text.length) {
                    appendInline(text[index].toString())
                    index++
                    handler.postDelayed(this, delayMs)
                } else {
                    onComplete()
                }
            }
        }
        handler.post(runnable)
    }

    private fun realDateText(): String {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val year = calendar.get(Calendar.YEAR)
        return "$month-$day-$year"
    }

    private fun realTimeText(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        return "%d:%02d:%02d".format(hour, minute, second)
    }

    private fun handleSubmittedLine(typed: String) {
        guard = true
        try {
            if (pendingKeyPress) {
                pendingKeyPress = false
                printPromptRaw()
                return
            }

            val field = pendingClockField
            if (field != null) {
                if (typed.isNotBlank()) {
                    when (field) {
                        "DATE" -> {
                            if (DosClockFormat.isValidDate(typed.trim())) {
                                shellContext.clock.dateText = typed.trim()
                            } else {
                                appendLineRaw("Fecha inválida")
                            }
                        }
                        "TIME" -> {
                            if (DosClockFormat.isValidTime(typed.trim())) {
                                shellContext.clock.timeText = typed.trim()
                            } else {
                                appendLineRaw("Hora inválida")
                            }
                        }
                    }
                }
                pendingClockField = null
                printPromptRaw()
                return
            }

            if (typed.isBlank()) {
                printPromptRaw()
                return
            }

            val result = interpreter.execute(typed)

            if (result.clearScreen) {
                consoleView.setText("")
            } else {
                result.output.forEach { appendLineRaw(it) }
                if (result.isError) {
                    val commandName = typed.trim().substringBefore(' ')
                    tryRunAsProgram(commandName, typed, result.errorMessage)
                }
            }

            when {
                result.awaitingClockField != null -> pendingClockField = result.awaitingClockField
                result.awaitingKeyPress -> pendingKeyPress = true
                else -> printPromptRaw()
            }
        } finally {
            guard = false
            promptStart = consoleView.text.length
            lockedText = consoleView.text.toString()
            scrollToBottom()
        }
    }

    /** Si COMMAND.COM no reconoci贸 la l铆nea como comando interno, se busca por PATH y se prueba como programa. */
    private fun tryRunAsProgram(commandName: String, fullLine: String, originalError: String?) {
        val args = fullLine.trim().split(Regex("\\s+")).drop(1)

        val resolved = try {
            EnvironmentManager.resolveExecutable(shellContext, commandName)
        } catch (e: VirtualFileSystemException) {
            null
        }

        if (resolved == null) {
            appendLineRaw(originalError ?: "'$commandName' no se reconoce como un comando interno o externo.")
            return
        }

        try {
            val process = programLoader.execute(resolved, args, shellContext)
            process.output.forEach { appendLineRaw(it) }
        } catch (e: RuntimeExecutionException) {
            appendLineRaw(e.message ?: "Error al ejecutar $commandName")
        }
    }

    private fun promptText(): String = "${shellContext.drive.driveLetter}:"

    private fun printPrompt() {
        guard = true
        consoleView.append("\n${promptText()}")
        guard = false
        promptStart = consoleView.text.length
        lockedText = consoleView.text.toString()
        scrollToBottom()
    }

    /** Como printPrompt, pero sin tocar `guard` (para usarse dentro de un bloque que ya lo maneja). */
    private fun printPromptRaw() {
        consoleView.append("\n${promptText()}")
    }

    private fun appendLine(line: String) {
        guard = true
        consoleView.append("\n$line")
        guard = false
        scrollToBottom()
    }

    private fun appendLineRaw(line: String) {
        consoleView.append("\n$line")
    }

    private fun appendInline(text: String) {
        guard = true
        consoleView.append(text)
        guard = false
        scrollToBottom()
    }

    private fun scrollToBottom() {
        consoleView.post { consoleView.setSelection(consoleView.text.length) }
    }
}
