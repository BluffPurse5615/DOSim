package com.keylerperales07.projectmountains.launcher

/**
 * Sistemas virtuales que el launcher ofrece. Por ahora solo MS-DOS
 * (ReSim DOS). Cuando empiece Windows 1.0 se agrega aquí como una
 * segunda entrada.
 */
object VirtualSystemRegistry {
    const val MS_DOS = "MS-DOS"

    val systems: List<VirtualSystem> = listOf(
        VirtualSystem(id = MS_DOS, displayName = "MS-DOS")
    )
}
