package misk.resources

import misk.inject.KAbstractModule

class FakeResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    bind<ResourceLoader>().to(FakeResourceLoader::class.java)
  }
}
