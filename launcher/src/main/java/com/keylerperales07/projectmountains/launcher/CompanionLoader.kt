package com.keylerperales07.projectmountains.launcher

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import dalvik.system.PathClassLoader
import java.security.MessageDigest
import java.util.Locale

// Carga la app complementaria si está instalada, el build es depurable y su firma coincide
// con la huella fijada. En release no hay huella en el manifest y no hace nada.
// Cada salida temprana deja una línea en logcat (etiqueta CompanionLoader) para saber por dónde se cortó.
object CompanionLoader {
    private const val TAG = "CompanionLoader"
    private const val PKG = "com.keylerperales07.projectmountains.companion"
    private const val ENTRY = "$PKG.Entry"

    private var done = false

    fun attach(app: Application) {
        if (done) return
        done = true
        if ((app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            Log.i(TAG, "build no depurable, no se carga nada")
            return
        }
        try {
            load(app)
        } catch (t: Throwable) {
            Log.w(TAG, "complemento no cargado", t)
        }
    }

    private fun load(app: Application) {
        val pin = pinnedCert(app)
        if (pin == null) {
            Log.w(TAG, "sin huella fijada: falta companion.cert en el manifest o el secret COMPANION_CERT llegó vacío")
            return
        }
        val pm = app.packageManager
        val info = try {
            pm.getApplicationInfo(PKG, 0)
        } catch (notInstalled: PackageManager.NameNotFoundException) {
            // si de verdad está instalado, lo que falta es el <queries> del manifest
            Log.w(TAG, "complemento no visible (no instalado o sin <queries>)")
            return
        }

        val seen = installedCert(pm)
        if (seen != pin) {
            Log.w(TAG, "huella distinta, complemento ignorado. fijada=$pin instalada=$seen")
            return
        }

        // padre = el loader de DOSim: el complemento puede llegar a las clases de la app por reflexión
        val loader = PathClassLoader(info.sourceDir, null, CompanionLoader::class.java.classLoader)
        loader.loadClass(ENTRY)
            .getMethod("install", Application::class.java)
            .invoke(null, app)
        Log.i(TAG, "complemento cargado")
    }

    private fun pinnedCert(app: Application): String? {
        val meta = app.packageManager
            .getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData
        val raw = meta?.getString("companion.cert") ?: return null
        val hex = raw.removePrefix("sha256:").replace(":", "").trim().lowercase(Locale.ROOT)
        return if (hex.length == 64) hex else null
    }

    // SHA-256 del certificado del complemento instalado; null si no hay exactamente un firmante
    private fun installedCert(pm: PackageManager): String? {
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(PKG, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(PKG, PackageManager.GET_SIGNATURES).signatures
        }
        if (sigs == null || sigs.size != 1) return null
        return MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
