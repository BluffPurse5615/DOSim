package com.keylerperales07.projectmountains.launcher

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Handler de crashes: en vez de dejar que el sistema muestre solo
 * "La app se detuvo", guarda el stack trace completo en un archivo
 * dentro de la app para poder leerlo después desde LauncherActivity,
 * sin necesitar logcat externo.
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val log = Log.getStackTraceString(throwable)
            crashLogFile(context).writeText(log)
        } catch (e: Exception) {
            // Si ni siquiera se puede guardar el log, no hay mucho más que hacer aquí.
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        fun crashLogFile(context: Context): File = File(context.filesDir, "last_crash.txt")

        fun install(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext))
        }
    }
}
