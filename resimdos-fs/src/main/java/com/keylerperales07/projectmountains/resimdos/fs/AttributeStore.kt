package com.keylerperales07.projectmountains.resimdos.fs

import org.json.JSONObject
import java.io.File

/**
 * El almacenamiento real de Android no tiene concepto de atributos
 * DOS (READ_ONLY, HIDDEN, SYSTEM, ARCHIVE) ni de fecha de creación
 * independiente de la de modificación. AttributeStore guarda esa
 * metadata extra en un archivo oculto (".resim_meta.json") dentro de
 * cada directorio real, uno por cada directorio virtual.
 *
 * Es un detalle interno de VirtualDrive: nada fuera de resimdos-fs
 * debería tocar esta clase directamente.
 */
internal class AttributeStore(private val realDirectory: File) {

    private val metaFile = File(realDirectory, META_FILE_NAME)

    private data class Entry(val attributes: Int, val createdAt: Long)

    private fun readAll(): MutableMap<String, Entry> {
        if (!metaFile.exists()) return mutableMapOf()
        val json = JSONObject(metaFile.readText())
        val result = mutableMapOf<String, Entry>()
        json.keys().forEach { name ->
            val obj = json.getJSONObject(name)
            result[name] = Entry(
                attributes = obj.getInt("attributes"),
                createdAt = obj.getLong("createdAt")
            )
        }
        return result
    }

    private fun writeAll(entries: Map<String, Entry>) {
        val json = JSONObject()
        entries.forEach { (name, entry) ->
            val obj = JSONObject()
            obj.put("attributes", entry.attributes)
            obj.put("createdAt", entry.createdAt)
            json.put(name, obj)
        }
        metaFile.writeText(json.toString())
    }

    fun getAttributes(name: String, defaultAttributes: Int): Int =
        readAll()[name]?.attributes ?: defaultAttributes

    fun getCreatedAt(name: String, defaultCreatedAt: Long): Long =
        readAll()[name]?.createdAt ?: defaultCreatedAt

    fun setAttributes(name: String, attributes: Int) {
        val all = readAll()
        val existing = all[name]
        all[name] = Entry(
            attributes = attributes,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        writeAll(all)
    }

    fun recordCreated(name: String, attributes: Int, createdAt: Long) {
        val all = readAll()
        all[name] = Entry(attributes = attributes, createdAt = createdAt)
        writeAll(all)
    }

    fun remove(name: String) {
        val all = readAll()
        if (all.remove(name) != null) writeAll(all)
    }

    fun rename(oldName: String, newName: String) {
        val all = readAll()
        val entry = all.remove(oldName)
        if (entry != null) {
            all[newName] = entry
            writeAll(all)
        }
    }

    companion object {
        const val META_FILE_NAME = ".resim_meta.json"

        /** Nombres reservados que nunca deben listarse como archivos virtuales. */
        fun isReserved(realFileName: String): Boolean = realFileName == META_FILE_NAME
    }
}
