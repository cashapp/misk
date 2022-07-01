package misk.cloud.gcp.security.encryption

import misk.config.Secret

data class EnvelopeEncryptionConfig(
  val project_id: String,
  val kek: Kek,
  val credentials: Secret<String>,
) {
  val kekUri = "gcp-kms://projects/$project_id/locations/${kek.location}/" +
    "keyRings/${kek.key_ring}/cryptoKeys/${kek.key_name}"
}

data class Kek(val location: String, val key_ring: String, val key_name: String)
