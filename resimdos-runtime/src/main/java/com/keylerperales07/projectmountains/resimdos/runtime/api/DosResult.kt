package com.keylerperales07.projectmountains.resimdos.runtime.api

/**
 * Resultado de una llamada a la DOS API. Al estilo de INT 21h (que
 * devuelve éxito o un código de error en vez de lanzar excepciones),
 * los programas virtuales reciben esto en vez de tener que capturar
 * excepciones de resimdos-fs directamente.
 */
sealed class DosResult<out T> {
    data class Ok<T>(val value: T) : DosResult<T>()
    data class Error(val message: String) : DosResult<Nothing>()

    fun getOrNull(): T? = (this as? Ok)?.value
    inline fun onOk(action: (T) -> Unit): DosResult<T> {
        if (this is Ok) action(value)
        return this
    }
    inline fun onError(action: (String) -> Unit): DosResult<T> {
        if (this is Error) action(message)
        return this
    }
}
