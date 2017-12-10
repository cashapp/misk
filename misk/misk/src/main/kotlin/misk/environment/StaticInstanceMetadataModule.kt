package misk.environment

import com.google.inject.AbstractModule

/** Provides a hard-coded set of instance metadata */
class StaticInstanceMetadataModule(val metadata: InstanceMetadata) : AbstractModule() {
  override fun configure() {
    bind(InstanceMetadata::class.java).toInstance(metadata)
  }
}
