package misk.resources

import misk.inject.KAbstractModule

class ResourceLoaderModule : KAbstractModule() {
  override fun configure() {
    bind<ResourceLoader>().toInstance(ClasspathResourceLoader)
  }
}
