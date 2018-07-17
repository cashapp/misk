package misk.resources

import com.google.inject.multibindings.MapBinder
import misk.inject.KAbstractModule

class ResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = MapBinder.newMapBinder(
        binder(), String::class.java, ResourceLoader.Backend::class.java)
    mapBinder.addBinding("/resources").toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding("/memory").to<MemoryResourceLoaderBackend>()
  }
}
