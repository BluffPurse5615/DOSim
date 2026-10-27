package com.keylerperales07.projectmountains.launcher

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText

/**
 * EditText de la consola cuyo desplazamiento vertical solo avanza.
 *
 * Un EditText multilínea con altura fija se desplaza a sí mismo: el
 * arrastre del dedo (Touch), el cursor que busca quedar visible
 * (bringPointIntoView) y scrollBy terminan todos en scrollTo(x, y).
 * Interceptando ese único punto basta para que, una vez que el texto
 * llegó al borde inferior y bajó, ninguna vía pueda subir otra vez.
 */

class LockedScrollEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    private var lowestScrollY = 0

    override fun scrollTo(x: Int, y: Int) {
        val floor = minOf(lowestScrollY, maxScrollY())
        val target = maxOf(y, floor)
        lowestScrollY = target
        super.scrollTo(x, target)
    }

    // Hay que llamarlo antes de vaciar el texto (CLS): si no, el piso viejo impide volver al inicio
    fun releaseScrollLock() {
        lowestScrollY = 0
    }

    private fun maxScrollY(): Int {
        val textLayout = layout ?: return 0
        val contentHeight = textLayout.height + totalPaddingTop + totalPaddingBottom
        return (contentHeight - height).coerceAtLeast(0)
    }
}