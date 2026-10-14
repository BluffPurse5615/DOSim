package com.keylerperales07.projectmountains.resimdos.runtime.api

import com.keylerperales07.projectmountains.resimdos.runtime.ProcessContext

/** Lectura/escritura de variables de entorno (las mismas que maneja SET en el shell). */
class EnvironmentApi(private val processContext: ProcessContext) {

    fun get(name: String): String? = processContext.shellContext.environment[name.uppercase()]

    fun set(name: String, value: String) {
        processContext.shellContext.environment[name.uppercase()] = value
    }

    fun all(): Map<String, String> = processContext.shellContext.environment.toMap()
}
