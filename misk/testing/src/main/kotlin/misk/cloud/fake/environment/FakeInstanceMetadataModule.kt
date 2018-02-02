package misk.cloud.fake.environment

import misk.inject.KAbstractModule
import misk.environment.InstanceMetadata

/** Provides a hard-coded set of instance metadata */
class FakeInstanceMetadataModule(val metadata: InstanceMetadata) : KAbstractModule() {
    override fun configure() {
        bind<InstanceMetadata>().toInstance(metadata)
    }
}
