package com.keylerperales07.projectmountains.resimdos.runtime.shell

/** Una fecha ya validada por [DosClockFormat.parseDate]. */
data class DosDate(val year: Int, val month: Int, val day: Int)

/** Una hora ya validada por [DosClockFormat.parseTime]; [centis] va de 0 a 99. */
data class DosTime(val hour: Int, val minute: Int, val second: Int, val centis: Int)

/**
 * Lo que DATE y TIME aceptan como entrada. Como en DOS 1.25, la fecha
 * es mes-día-año (con - o /) y el año va de 80 a 99 o de 1980 a 2099;
 * la hora es hh:mm[:ss[.cc]]. Si algo no encaja (también 31 de
 * febrero o las 25 horas) devuelven null y el comando responde
 * "Invalid date" / "Invalid time".
 */
object DosClockFormat {
    private val DATE_REGEX = Regex("""^(\d{1,2})[-/](\d{1,2})[-/](\d{2}|\d{4})$""")
    private val TIME_REGEX = Regex("""^(\d{1,2}):(\d{1,2})(?::(\d{1,2})(?:\.(\d{1,2}))?)?$""")

    fun parseDate(text: String): DosDate? {
        val match = DATE_REGEX.matchEntire(text.trim()) ?: return null
        val month = match.groupValues[1].toInt()
        val day = match.groupValues[2].toInt()
        val yearText = match.groupValues[3]
        val year = if (yearText.length == 2) {
            val short = yearText.toInt()
            if (short < 80) return null
            1900 + short
        } else {
            yearText.toInt().also { if (it !in 1980..2099) return null }
        }
        if (month !in 1..12 || day < 1 || day > daysInMonth(year, month)) return null
        return DosDate(year, month, day)
    }

    fun parseTime(text: String): DosTime? {
        val match = TIME_REGEX.matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues[3].ifEmpty { "0" }.toInt()
        // ".5" son 50 centésimas; ".05" son 5.
        val centisText = match.groupValues[4]
        val centis = when (centisText.length) {
            0 -> 0
            1 -> centisText.toInt() * 10
            else -> centisText.toInt()
        }
        if (hour > 23 || minute > 59 || second > 59) return null
        return DosTime(hour, minute, second, centis)
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}