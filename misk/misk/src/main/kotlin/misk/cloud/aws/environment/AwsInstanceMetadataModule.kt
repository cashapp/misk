package misk.cloud.aws.environment

import misk.environment.InstanceMetadata
import misk.inject.KAbstractModule
import javax.inject.Singleton

/** Retrieves instance metadata for applications running on AWS */
class AwsInstanceMetadataModule : KAbstractModule() {
  override fun configure() {
    bind<InstanceMetadata>()
        .toProvider(AwsInstanceMetadataProvider::class.java)
        .`in`(Singleton::class.java)
  }
}
