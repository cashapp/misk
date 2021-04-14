package misk.cloud.gcp.storage

import misk.cloud.gcp.TransportConfig
import wisp.config.Config

/** Configuration for talking to Google Cloud Storage */
data class StorageConfig(
  val use_local_storage: Boolean = false,
  val local_storage: LocalStorageConfig? = null,
  val transport: TransportConfig = TransportConfig()
) : Config

/** Configuration for local (emulated) storage */
data class LocalStorageConfig(val data_dir: String)
