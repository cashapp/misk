package misk.resources

import com.google.inject.multibindings.MapBinder
import misk.inject.KAbstractModule

class ResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = MapBinder.newMapBinder(
        binder(), String::class.java, ResourceLoader.Backend::class.java)
    mapBinder.addBinding("classpath:").toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding("filesystem:").toInstance(FilesystemLoaderBackend)
    mapBinder.addBinding("memory:").to<MemoryResourceLoaderBackend>()
  }
}

/**
 * Can be used instead of [ResourceLoaderModule] in tests to load filesystem: resources from the
 * classpath instead.
 *
 * The files should be located at the same path within the classpath. For example, if loading
 * filesystem:/etc/secrets/password.txt, the file must exist at
 * src/test/resources/etc/secrets/password.txt
 */
class TestingResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = MapBinder.newMapBinder(
        binder(), String::class.java, ResourceLoader.Backend::class.java)
    mapBinder.addBinding("classpath:").toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding("filesystem:").toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding("memory:").to<MemoryResourceLoaderBackend>()
  }
}
