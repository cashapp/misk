package misk.resources

import com.google.inject.multibindings.ProvidesIntoMap
import com.google.inject.multibindings.StringMapKey
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import wisp.resources.ClasspathResourceLoaderBackend
import wisp.resources.EnvironmentResourceLoaderBackend
import wisp.resources.FakeFilesystemLoaderBackend
import wisp.resources.FilesystemLoaderBackend
import wisp.resources.MemoryResourceLoaderBackend
import wisp.resources.FakeResourceLoaderBackend
import wisp.resources.OnePasswordResourceLoaderBackend
import wisp.resources.ResourceLoader as WispResourceLoader

class ResourceLoaderModule @JvmOverloads constructor(private val isReal: Boolean = true) : KAbstractModule() {
  override fun configure() {
    val mapBinder = newMapBinder<String, WispResourceLoader.Backend>()

    mapBinder.addBinding(ClasspathResourceLoaderBackend.SCHEME).toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding(FilesystemLoaderBackend.SCHEME).toInstance(FilesystemLoaderBackend)
    mapBinder.addBinding(MemoryResourceLoaderBackend.SCHEME).toInstance(MemoryResourceLoaderBackend())
    mapBinder.addBinding(EnvironmentResourceLoaderBackend.SCHEME).toInstance(EnvironmentResourceLoaderBackend)
    if (!isReal) {
      mapBinder.addBinding(OnePasswordResourceLoaderBackend.SCHEME).toInstance(OnePasswordResourceLoaderBackend)
    }
  }
}

/**
 * Can be used instead of [ResourceLoaderModule] in tests to load filesystem: resources using
 * [FakeFilesystemLoaderBackend]
 */
class TestingResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    val mapBinder = newMapBinder<String, WispResourceLoader.Backend>()

    mapBinder.addBinding(ClasspathResourceLoaderBackend.SCHEME).toInstance(ClasspathResourceLoaderBackend)
    mapBinder.addBinding(MemoryResourceLoaderBackend.SCHEME).toInstance(MemoryResourceLoaderBackend())
    mapBinder.addBinding(EnvironmentResourceLoaderBackend.SCHEME).toInstance(EnvironmentResourceLoaderBackend)

    newMapBinder<String, String>(ForFakeFiles::class)
  }

  @ProvidesIntoMap
  @StringMapKey("filesystem:")
  @Singleton
  @Suppress("unused")
  internal fun fakeFilesystemLoaderBackend(
    @ForFakeFiles fakeFiles: Map<String, String>
  ): WispResourceLoader.Backend = FakeFilesystemLoaderBackend(fakeFiles)

  @ProvidesIntoMap
  @StringMapKey(OnePasswordResourceLoaderBackend.SCHEME)
  @Singleton
  @Suppress("unused")
  internal fun fakeOnePasswordResourceLoaderBackend(
    @ForFakeFiles fakeFiles: Map<String, String>
  ): WispResourceLoader.Backend = FakeResourceLoaderBackend(fakeFiles)
}

@Qualifier
annotation class ForFakeFiles
