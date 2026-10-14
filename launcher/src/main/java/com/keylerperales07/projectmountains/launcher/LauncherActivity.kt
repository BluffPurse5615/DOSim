package com.keylerperales07.projectmountains.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Punto central desde el cual el usuario elige qué sistema virtual
 * abrir. Construida con vistas de Android planas (sin layouts XML ni
 * recursos) para no depender de resolución de recursos entre módulos.
 * Envuelta en el mismo "monitor" de 640x480 que usa DosConsoleActivity,
 * para que ambas pantallas se vean consistentes.
 */
class LauncherActivity : Activity() {

    /** Mismo truco anti-DPI que DosConsoleActivity: dp/sp siempre equivalen al mismo número de píxeles. */
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT
        val fixedDensityContext = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(fixedDensityContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(48, 96, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val monitor = AspectRatioFrameLayout(this).apply {
            aspectRatioWidth = 640
            aspectRatioHeight = 480
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            addView(root)
        }

        val screenBackground = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(monitor)
        }

        val dosTypeface = try {
            Typeface.createFromAsset(assets, "fonts/dos-vga.ttf")
        } catch (e: Exception) {
            Typeface.MONOSPACE
        }

        val title = TextView(this).apply {
            text = "Simulación Recreada"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = dosTypeface
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }
        root.addView(title)

        VirtualSystemRegistry.systems.forEach { system ->
            val button = Button(this).apply {
                text = system.displayName
                setOnClickListener { openSystem(system) }
            }
            root.addView(button, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24 })
        }

        setContentView(screenBackground)
        enableFullscreen()
        showLastCrashIfAny()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableFullscreen()
    }

    /** Oculta la barra de estado y la de navegación — igual que en DosConsoleActivity. */
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

    private fun showLastCrashIfAny() {
        val crashFile = CrashHandler.crashLogFile(this)
        if (!crashFile.exists()) return

        val log = crashFile.readText()
        AlertDialog.Builder(this)
        .setTitle("Último crash")
        .setMessage(log)
        .setPositiveButton("Cerrar", null)
        .setNegativeButton("Borrar") { _, _ -> crashFile.delete() }
        .show()
    }

    private fun openSystem(system: VirtualSystem) {
        when (system.id) {
            VirtualSystemRegistry.MS_DOS -> startActivity(Intent(this, DosConsoleActivity::class.java))
        }
    }
}
