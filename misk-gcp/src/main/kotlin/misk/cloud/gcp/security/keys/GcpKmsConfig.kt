package misk.cloud.gcp.security.keys

import wisp.config.Config

data class GcpKmsConfig(
  val project_id: String,
  val key_locations: Map<String, GcpKeyLocation>
) : Config

data class GcpKeyLocation(val location: String, val key_ring: String, val key_name: String) {
  val path = "locations/$location/keyRings/$key_ring/cryptoKeys/$key_name"
}
