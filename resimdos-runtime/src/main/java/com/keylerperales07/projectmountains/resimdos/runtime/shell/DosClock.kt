package com.keylerperales07.projectmountains.resimdos.runtime.shell

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Fecha y hora de una sesión de DOS. Las PC de la época de DOS 1.25
 * no tenían reloj con batería: arrancaban en 1-01-1980 y el reloj
 * corría solo desde ahí (o desde lo que el usuario escribiera con
 * DATE y TIME).
 *
 * Este reloj hace lo mismo: guarda un punto de partida y cuenta el
 * tiempo transcurrido con [nanoClock] (por defecto System.nanoTime,
 * un contador que avanza solo y no se mueve si cambian la hora del
 * teléfono, la zona horaria o el horario de verano). Así la hora de
 * DOS corre sin volver a consultar la hora del sistema.
 *
 * Internamente la fecha vive en UTC solo para hacer las cuentas;
 * nunca se convierte a la zona horaria del dispositivo.
 */
class DosClock(private val nanoClock: () -> Long = System::nanoTime) {

    private var anchorNanos = nanoClock()
    private var anchorMillis = utcMillis(1980, 1, 1, 0, 0, 0, 0)

    /** "Thu 10-29-2026", con el mes a dos columnas como DOS ("Tue  1-01-1980"). */
    val dateText: String
        get() {
            val c = now()
            val day = DAY_NAMES[c.get(Calendar.DAY_OF_WEEK) - 1]
            return "%s %2d-%02d-%d".format(
                Locale.ROOT, day, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.YEAR)
            )
        }

    /** "18:07:09.64": horas sin relleno, centésimas al final. */
    val timeText: String
        get() {
            val c = now()
            return "%d:%02d:%02d.%02d".format(
                Locale.ROOT,
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
                c.get(Calendar.SECOND),
                c.get(Calendar.MILLISECOND) / 10
            )
        }

    /** DATE: cambia el día y deja la hora corriendo. */
    fun setDate(date: DosDate) {
        val c = now()
        restart(
            utcMillis(
                date.year, date.month, date.day,
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), c.get(Calendar.MILLISECOND)
            )
        )
    }

    /** TIME: cambia la hora y deja el día corriendo. */
    fun setTime(time: DosTime) {
        val c = now()
        restart(
            utcMillis(
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                time.hour, time.minute, time.second, time.centis * 10
            )
        )
    }

    /** Toma una sola vez los números de [local] (fecha y hora del dispositivo) como punto de partida. */
    fun startFrom(local: Calendar) {
        restart(
            utcMillis(
                local.get(Calendar.YEAR), local.get(Calendar.MONTH) + 1, local.get(Calendar.DAY_OF_MONTH),
                local.get(Calendar.HOUR_OF_DAY), local.get(Calendar.MINUTE), local.get(Calendar.SECOND),
                local.get(Calendar.MILLISECOND)
            )
        )
    }

    private fun restart(millis: Long) {
        anchorMillis = millis
        anchorNanos = nanoClock()
    }

    private fun now(): Calendar {
        val elapsedMillis = (nanoClock() - anchorNanos) / 1_000_000
        return Calendar.getInstance(UTC).apply { timeInMillis = anchorMillis + elapsedMillis }
    }

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
        val DAY_NAMES = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, millis: Int): Long =
            Calendar.getInstance(UTC).apply {
                clear()
                set(year, month - 1, day, hour, minute, second)
                set(Calendar.MILLISECOND, millis)
            }.timeInMillis
    }
}
