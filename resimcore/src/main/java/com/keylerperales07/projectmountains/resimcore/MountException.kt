package com.keylerperales07.projectmountains.resimcore

sealed class MountException(message: String) : Exception(message) {

    class DriveAlreadyMountedException(driveLetter: Char) :
        MountException("La unidad $driveLetter: ya está montada")

    class DriveNotMountedException(driveLetter: Char) :
        MountException("La unidad $driveLetter: no está montada")
}
