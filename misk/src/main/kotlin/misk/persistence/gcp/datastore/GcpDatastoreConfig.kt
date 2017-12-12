package misk.persistence.gcp.datastore

import misk.config.Config

data class GcpDatastoreConfig(
  val namespace: String?
) : Config
