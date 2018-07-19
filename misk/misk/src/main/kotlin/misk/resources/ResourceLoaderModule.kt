package misk.resources

import com.google.inject.multibindings.MapBinder
import misk.inject.KAbstractModule

internal class ResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = MapBinder.newMapBinder(
        binder(), String::class.java, ResourceLoader.Backend::class.java)
    mapBinder.addBinding("classpath:").toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding("filesystem:").toInstance(FilesystemLoaderBackend)
    mapBinder.addBinding("memory:").to<MemoryResourceLoaderBackend>()
  }
}
