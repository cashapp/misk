package misk.resources

import com.google.inject.Provides
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.ProvidesIntoMap
import com.google.inject.multibindings.StringMapKey
import misk.inject.KAbstractModule
import wisp.resources.ClasspathResourceLoaderBackend
import wisp.resources.FakeFilesystemLoaderBackend
import wisp.resources.FilesystemLoaderBackend
import wisp.resources.MemoryResourceLoaderBackend
import wisp.resources.ResourceLoader.Backend
import javax.inject.Qualifier
import javax.inject.Singleton
import wisp.resources.ResourceLoader as WispResourceLoader

class ResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = MapBinder.newMapBinder(
      binder(), String::class.java, WispResourceLoader.Backend::class.java
    )
    mapBinder.addBinding("classpath:").toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding("filesystem:").toInstance(FilesystemLoaderBackend)
    mapBinder.addBinding("memory:").toInstance(MemoryResourceLoaderBackend())
  }

  @Provides
  internal fun resourceLoader(
    backends: java.util.Map<String, WispResourceLoader.Backend>
  ): ResourceLoader = ResourceLoader(backends)
}

/**
 * Can be used instead of [ResourceLoaderModule] in tests to load filesystem: resources using
 * [FakeFilesystemLoaderBackend]
 */
class TestingResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = MapBinder.newMapBinder(
      binder(), String::class.java, WispResourceLoader.Backend::class.java
    )
    mapBinder.addBinding("classpath:").toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding("memory:").toInstance(MemoryResourceLoaderBackend())

    newMapBinder<String, String>(ForFakeFiles::class)
  }

  @ProvidesIntoMap
  @StringMapKey("filesystem:")
  @Singleton
  @Suppress("unused")
  internal fun fakeFilesystemLoaderBackend(
    @ForFakeFiles fakeFiles: Map<String, String>
  ): WispResourceLoader.Backend = FakeFilesystemLoaderBackend(fakeFiles)

  @Provides
  internal fun resourceLoader(
    backends: java.util.Map<String, WispResourceLoader.Backend>
  ): ResourceLoader = ResourceLoader(backends)
}

@Qualifier
annotation class ForFakeFiles

/**
 * ResourceLoader is a testable API for loading resources from the classpath, from the filesystem,
 * from memory, or from another [Backend] source.
 *
 * Resource addresses have a scheme name, a colon, and an absolute filesystem-like path:
 * `classpath:/migrations/v1.sql`. Schemes identify backends `classpath:` or `memory:`. Paths start
 * with a slash and have any number of segments.
 *
 * **Classpath resources** use the scheme `classpath:`. The backend reads data from the
 * `src/main/resources` of the project's modules and the contents of all library `.jar` files.
 * Classpath resources are read-only.
 *
 * **Filesystem resources** use the scheme `filesystem:`. The backend reads data from the host
 * machine's local filesystem. It is read-only and does not support [list].
 *
 * **Memory resources** use the scheme `memory:`. The backend starts empty and is populated by calls
 * to [put].
 *
 * Other backends are permitted. They should be registered with a `MapBinder` with the backend
 * scheme like `classpath:` as the key.
 */
typealias ResourceLoader = WispResourceLoader
