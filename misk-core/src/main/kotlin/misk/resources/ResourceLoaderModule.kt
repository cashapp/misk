package misk.resources

import com.google.inject.Provides
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.ProvidesIntoMap
import com.google.inject.multibindings.StringMapKey
import misk.inject.KAbstractModule
import javax.inject.Qualifier
import javax.inject.Singleton

class ResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = MapBinder.newMapBinder(
      binder(), String::class.java, ResourceLoader.Backend::class.java
    )
    mapBinder.addBinding("classpath:").toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding("filesystem:").toInstance(FilesystemLoaderBackend)
    mapBinder.addBinding("memory:").toInstance(MemoryResourceLoaderBackend())
  }

  @Provides
  internal fun resourceLoader(
    backends: java.util.Map<String, ResourceLoader.Backend>
  ): ResourceLoader = ResourceLoader(backends)
}

/**
 * Can be used instead of [ResourceLoaderModule] in tests to load filesystem: resources using
 * [FakeFilesystemLoaderBackend]
 */
class TestingResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = MapBinder.newMapBinder(
      binder(), String::class.java, ResourceLoader.Backend::class.java
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
  ): ResourceLoader.Backend = FakeFilesystemLoaderBackend(fakeFiles)

  @Provides
  internal fun resourceLoader(
    backends: java.util.Map<String, ResourceLoader.Backend>
  ): ResourceLoader = ResourceLoader(backends)
}

@Qualifier
annotation class ForFakeFiles
