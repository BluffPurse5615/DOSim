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
object CompanionLoader {
    private const val TAG = "CompanionLoader"
    private const val PKG = "com.keylerperales07.projectmountains.companion"
    private const val ENTRY = "$PKG.Entry"

    private var done = false

    fun attach(app: Application) {
        if (done) return
        done = true
        if ((app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        try {
            load(app)
        } catch (t: Throwable) {
            Log.w(TAG, "complemento no cargado", t)
        }
    }

    private fun load(app: Application) {
        val pin = pinnedCert(app) ?: return
        val pm = app.packageManager
        val info = try {
            pm.getApplicationInfo(PKG, 0)
        } catch (notInstalled: PackageManager.NameNotFoundException) {
            return
        }

        if (!matches(pm, pin)) {
            Log.w(TAG, "huella distinta, complemento ignorado")
            return
        }

        // padre = el loader de DOSim: el complemento puede llegar a las clases de la app por reflexión
        val loader = PathClassLoader(info.sourceDir, null, CompanionLoader::class.java.classLoader)
        loader.loadClass(ENTRY)
            .getMethod("install", Application::class.java)
            .invoke(null, app)
    }

    private fun pinnedCert(app: Application): String? {
        val meta = app.packageManager
            .getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData
        val raw = meta?.getString("companion.cert") ?: return null
        val hex = raw.removePrefix("sha256:").replace(":", "").trim().lowercase(Locale.ROOT)
        return if (hex.length == 64) hex else null
    }

    private fun matches(pm: PackageManager, pin: String): Boolean {
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(PKG, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(PKG, PackageManager.GET_SIGNATURES).signatures
        }
        if (sigs == null || sigs.size != 1) return false
        val seen = MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return seen == pin
    }
}
