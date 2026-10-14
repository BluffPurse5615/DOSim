package com.keylerperales07.projectmountains.resimdos.runtime.shell

/**
 * DATE/TIME de DOS real no aceptan cualquier texto: validan un
 * formato básico antes de guardar el valor nuevo. Esto no calcula
 * si la fecha existe de verdad (ej. 31 de febrero) — solo exige la
 * forma general "número-número-número" / "número:número[:número]".
 */
object DosClockFormat {
    private val DATE_REGEX = Regex("""^\d{1,2}[-/]\d{1,2}[-/]\d{2,4}$""")
    private val TIME_REGEX = Regex("""^\d{1,2}:\d{2}(:\d{2}(\.\d{1,2})?)?$""")

    fun isValidDate(value: String): Boolean = DATE_REGEX.matches(value.trim())
    fun isValidTime(value: String): Boolean = TIME_REGEX.matches(value.trim())
}
