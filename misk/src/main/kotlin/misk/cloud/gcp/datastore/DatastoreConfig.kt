package misk.cloud.gcp.datastore

import misk.cloud.gcp.TransportConfig
import misk.config.Config

/** Configuration for talking to Google datastore */
data class DatastoreConfig(
    val transport: TransportConfig = TransportConfig()
) : Config
