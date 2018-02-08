package misk.cloud.gcp.storage

import misk.cloud.gcp.TransportConfig
import misk.config.Config

/** Configuration for talking to Google Cloud Storage */
data class StorageConfig(
        val transport : TransportConfig = TransportConfig()
) : Config