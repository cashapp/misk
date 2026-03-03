package misk.resources

import misk.inject.KAbstractModule

/**
 * Adds the provided fake files to the map used by [FilesystemLoaderBackend].
 */
class FakeFilesModule(private val fakeFiles: Map<String, String>) : KAbstractModule() {
  override fun configure() {
    fakeFiles.forEach {
        newMapBinder<String, String>(ForFakeFiles::class)
            .addBinding(it.key)
            .toInstance(it.value)
    }
  }
}
