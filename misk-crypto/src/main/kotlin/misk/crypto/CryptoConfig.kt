package misk.crypto

import misk.config.Config
import misk.config.Secret

/**
 * Main configuration object representing to be used in the app.
 */
data class CryptoConfig(
  val keys: List<Key>
) : Config

data class Key(
  val key_name: String,
  val details: List<EncryptedKey>
) : Config

data class EncryptedKey(
  val json_key_spec: Secret<String>,
  val aws_kms_key_alias: String? = null,
  val gcp_key_uri: String? = null
)