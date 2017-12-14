package misk.testing

import com.google.inject.Module

interface ModuleProvider {
    fun modules() : Array<out Module>
}
