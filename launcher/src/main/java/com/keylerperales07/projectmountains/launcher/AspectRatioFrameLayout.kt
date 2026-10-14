package com.keylerperales07.projectmountains.launcher

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Hace que su único hijo se comporte como si la pantalla tuviera
 * EXACTAMENTE [aspectRatioWidth] x [aspectRatioHeight] píxeles reales
 * (640x480 por defecto), sin importar la resolución real del
 * teléfono, y después lo escala (con scaleX/scaleY) para llenar el
 * espacio disponible — igual que un emulador retro que dibuja sobre
 * un framebuffer de resolución fija y lo estira a pantalla completa.
 *
 * Esto es distinto de solo "darle forma 4:3 a una caja": acá el hijo
 * de verdad se MIDE y se DISEÑA (layout) en 640x480 píxeles — un
 * texto de 14sp ocupa 14 píxeles de esos 640x480, siempre, en
 * cualquier teléfono — y luego Android escala esa imagen ya resuelta
 * para que se vea del tamaño de la pantalla real. El toque (tap,
 * cursor del EditText, etc.) sigue funcionando bien porque Android ya
 * toma en cuenta la escala del hijo al traducir las coordenadas del
 * toque a su sistema de coordenadas interno.
 *
 * La relación se puede fijar desde XML con los atributos propios
 * app:aspectRatioWidth / app:aspectRatioHeight (ver res/values/attrs.xml);
 * si no se especifican, se usa el valor por defecto (640x480).
 *
 * Con [forceAspectRatio] en false se comporta como un FrameLayout común.
 */
class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var forceAspectRatio: Boolean = true
    var aspectRatioWidth: Int = 320
    var aspectRatioHeight: Int = 200

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AspectRatioFrameLayout)
            aspectRatioWidth = typedArray.getInt(R.styleable.AspectRatioFrameLayout_aspectRatioWidth, aspectRatioWidth)
            aspectRatioHeight = typedArray.getInt(R.styleable.AspectRatioFrameLayout_aspectRatioHeight, aspectRatioHeight)
            // Un TypedArray toma un recurso compartido internamente; si no se
            // libera con recycle(), ese recurso queda retenido y con muchas
            // vistas creadas (o recreaciones de pantalla) puede degradar memoria.
            typedArray.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        if (!forceAspectRatio) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // El hijo se mide SIEMPRE con el tamaño fijo real en píxeles
        // (ej. 640x480), sin importar cuánto espacio real haya en
        // pantalla — eso es lo que lo hace una "resolución" de
        // verdad y no solo una proporción.
        val fixedWidthSpec = MeasureSpec.makeMeasureSpec(aspectRatioWidth, MeasureSpec.EXACTLY)
        val fixedHeightSpec = MeasureSpec.makeMeasureSpec(aspectRatioHeight, MeasureSpec.EXACTLY)
        measureChildren(fixedWidthSpec, fixedHeightSpec)

        // El contenedor en sí ocupa lo que el padre real le dé (pantalla completa).
        setMeasuredDimension(parentWidth, parentHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return

        if (!forceAspectRatio) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }

        // El hijo se posiciona en su propio sistema de coordenadas de
        // 640x480, en la esquina (0,0) — después lo escalamos.
        child.layout(0, 0, aspectRatioWidth, aspectRatioHeight)

        val scaleX = width.toFloat() / aspectRatioWidth
        val scaleY = height.toFloat() / aspectRatioHeight
        // El menor de los dos: encaja completo dentro de la pantalla
        // real sin recortarse (deja franjas negras si no encaja justo).
        val scale = minOf(scaleX, scaleY)

        child.scaleX = scale
        child.scaleY = scale
        child.pivotX = 0f
        child.pivotY = 0f
        // Centra el rectángulo ya escalado dentro del espacio real disponible.
        child.translationX = (width - aspectRatioWidth * scale) / 2f
        child.translationY = (height - aspectRatioHeight * scale) / 2f
    }
}
