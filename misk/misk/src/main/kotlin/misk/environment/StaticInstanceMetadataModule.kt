package misk.environment

import misk.inject.KAbstractModule

/** Provides a hard-coded set of instance metadata */
class StaticInstanceMetadataModule(val metadata: InstanceMetadata) : KAbstractModule() {
  override fun configure() {
    bind<InstanceMetadata>().toInstance(metadata)
  }
}
