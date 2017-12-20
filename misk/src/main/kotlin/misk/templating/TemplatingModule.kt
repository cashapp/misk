package misk.templating

import com.google.inject.Provides
import com.google.inject.Singleton
import com.hubspot.jinjava.Jinjava
import com.hubspot.jinjava.JinjavaConfig
import com.hubspot.jinjava.loader.ResourceLocator
import misk.inject.KAbstractModule
import misk.resources.ResourceLoader

class TemplatingModule : KAbstractModule() {
    override fun configure() {
    }

    @Provides
    @Singleton
    fun provideJinjava(): Jinjava {
        val jinjava = Jinjava(JinjavaConfig())
        jinjava.resourceLocator = ResourceLocator { resourcePath, _, _ ->
            ResourceLoader.utf8("admin/$resourcePath") ?: throw IllegalArgumentException()
        }
        return jinjava
    }
}
