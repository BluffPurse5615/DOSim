package com.keylerperales07.projectmountains.resimdos.runtime.shell

/**
 * Fecha y hora "del sistema" dentro de una sesión de DOS. Las PC de
 * la época de DOS 1.25 no tenían reloj con batería: al arrancar
 * siempre mostraban la misma fecha por defecto y el tiempo empezaba
 * a contarse desde cero, hasta que el usuario los corregía a mano.
 */
data class DosClock(
    var dateText: String = "Tue  1-01-1980",
    var timeText: String = "0:00:00.00"
)
