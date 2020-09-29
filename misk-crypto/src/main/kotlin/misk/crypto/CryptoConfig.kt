package misk.crypto

import misk.config.Config
import misk.config.Secret

/**
 * Main configuration object representing to be used in the app.
 */
data class CryptoConfig(
  val keys: List<Key>?,
  /**
   * The KMS' key URI.
   * For GCP users that may look like:
   * gcp-kms://projects/<project>/locations/<location>/keyRings/<keyRing>/cryptoKeys/<key>
   * For AWS users that the Key URI looks like:
   * aws-kms://arn:aws:kms:<region>:<account-id>:key/<key-id>
   */
  val kms_uri: String,
  /**
   * The key aliases we want to use from an external key manager
   */
  val external_data_keys: Map<KeyAlias, KeyType>? = null
) : Config

/**
 * Describes a specific key
 */
data class Key(
  /**
   * Descriptive short string for the key.
   * This name will be used when injecting the corresponding key object in the app, like:
   * ```
   * @Inject @Named("keyName") lateinit var myKey: Aead
   * ```
   */
  val key_name: String,
  /**
   * Type of Tink primitive to initialize
   */
  val key_type: KeyType,
  /**
   * In config it's the path to a file containing the encrypted key material in Tink's JSON format.
   * However MiskConfig will read the contents of the file, so this variable is file's contents.
   */
  val encrypted_key: Secret<String>,
  /**
   * A key-specific KMS uri
   */
  val kms_uri: String? = null
) : Config

/**
 * Supported key types
 */
enum class KeyType {
  AEAD,
  DAEAD,
  MAC,
  DIGITAL_SIGNATURE,
  HYBRID_ENCRYPT,
  HYBRID_ENCRYPT_DECRYPT,
  STREAMING_AEAD,
  PGP_DECRYPT,
  PGP_ENCRYPT
}
