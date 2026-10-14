package com.keylerperales07.projectmountains.resimcore

import android.content.Context
import com.keylerperales07.projectmountains.resimdos.fs.VirtualDrive
import java.io.File

/**
 * Motor común de Simulación Recreada.
 *
 * ReSimCore no sabe nada de DOS, comandos ni programas: solo se
 * encarga de montar/desmontar unidades virtuales (Resim-FS) y
 * entregarlas a quien las pida (resimdos-runtime,
 * y en el futuro reswin). Cada sistema recreado
 * (ReSim DOS, luego Windows) monta las unidades que necesite sobre
 * la misma instancia de ReSimCore.
 *
 * Pensado para vivir como una única instancia por proceso, típicamente
 * guardada en la clase Application.
 */
class ReSimCore {

    private val mountedDrives = mutableMapOf<Char, VirtualDrive>()

    /**
     * Monta una unidad en [driveLetter] usando [realRoot] como
     * almacenamiento real de respaldo. Si [realRoot] no existe, se crea.
     */
    @Synchronized
    fun mount(driveLetter: Char, realRoot: File): VirtualDrive {
        val letter = driveLetter.uppercaseChar()
        if (mountedDrives.containsKey(letter)) {
            throw MountException.DriveAlreadyMountedException(letter)
        }
        val drive = VirtualDrive(letter, realRoot)
        mountedDrives[letter] = drive
        return drive
    }

    @Synchronized
    fun unmount(driveLetter: Char) {
        val letter = driveLetter.uppercaseChar()
        if (mountedDrives.remove(letter) == null) {
            throw MountException.DriveNotMountedException(letter)
        }
    }

    @Synchronized
    fun getDrive(driveLetter: Char): VirtualDrive? = mountedDrives[driveLetter.uppercaseChar()]

    @Synchronized
    fun requireDrive(driveLetter: Char): VirtualDrive =
    getDrive(driveLetter) ?: throw MountException.DriveNotMountedException(driveLetter.uppercaseChar())

    @Synchronized
    fun isMounted(driveLetter: Char): Boolean = mountedDrives.containsKey(driveLetter.uppercaseChar())

    @Synchronized
    fun mountedDriveLetters(): List<Char> = mountedDrives.keys.sorted()

    companion object {
        /**
         * Monta la unidad A: (DOS 1.25 es de disquete, sin disco duro
         * todavía) dentro de Android/data/<paquete>/files/ReSim-FS/A
         * — almacenamiento externo propio de la app, visible desde un
         * explorador de archivos, sin pedir permisos especiales. Ahí
         * debe existir COMMAND.COM y los comandos externos como
         * archivos reales.
         */
        fun mountDefaultDosDrive(core: ReSimCore, context: Context): VirtualDrive {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            val root = File(base, "ReSim-FS/A")
            return core.mount('A', root)
        }
    }
}
