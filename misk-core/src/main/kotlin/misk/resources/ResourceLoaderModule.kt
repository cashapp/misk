package misk.resources

import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.ProvidesIntoMap
import com.google.inject.multibindings.StringMapKey
import misk.inject.KAbstractModule
import wisp.resources.ClasspathResourceLoaderBackend
import wisp.resources.FakeFilesystemLoaderBackend
import wisp.resources.FilesystemLoaderBackend
import wisp.resources.MemoryResourceLoaderBackend
import javax.inject.Qualifier
import javax.inject.Singleton
import wisp.resources.ResourceLoader as WispResourceLoader

@Deprecated("Use from misk-config instead")
class ResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = MapBinder.newMapBinder(
      binder(), String::class.java, WispResourceLoader.Backend::class.java
    )
    mapBinder.addBinding("classpath:").toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding("filesystem:").toInstance(FilesystemLoaderBackend)
    mapBinder.addBinding("memory:").toInstance(MemoryResourceLoaderBackend())
  }
}

/**
 * Can be used instead of [ResourceLoaderModule] in tests to load filesystem: resources using
 * [FakeFilesystemLoaderBackend]
 */
@Deprecated("Use from misk-config instead")
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
}

@Deprecated("Use from misk-config instead")
@Qualifier
annotation class ForFakeFiles
