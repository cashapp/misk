package misk.cloud.fake.environment

import misk.environment.InstanceMetadata
import misk.inject.KAbstractModule

/** Provides a hard-coded set of instance metadata */
class FakeInstanceMetadataModule(val metadata: InstanceMetadata) : KAbstractModule() {
  override fun configure() {
    bind<InstanceMetadata>().toInstance(metadata)
  }
}
