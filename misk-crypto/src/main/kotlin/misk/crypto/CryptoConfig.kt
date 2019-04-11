package misk.crypto

import misk.config.Config
import misk.config.Secret

/**
 * Main configuration object representing to be used in the app.
 */
data class CryptoConfig(
  val keys: List<Key>?,
  val kms_uri: String
) : Config

data class Key(
  val key_name: String,
  val encrypted_key: Secret<String>
) : Config