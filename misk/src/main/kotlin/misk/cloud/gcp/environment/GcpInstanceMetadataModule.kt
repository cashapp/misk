package misk.cloud.gcp.environment

import misk.environment.InstanceMetadata
import misk.inject.KAbstractModule
import javax.inject.Singleton

/** Retrieves instance metadata for applications running on GCP */
class GcpInstanceMetadataModule : KAbstractModule() {
  override fun configure() {
    bind<InstanceMetadata>()
        .toProvider(GcpInstanceMetadataProvider::class.java)
        .`in`(Singleton::class.java)
  }
}
