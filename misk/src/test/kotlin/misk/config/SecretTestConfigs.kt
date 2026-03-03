package misk.config

data class SuperSecretConfig(
  val string_value: String,
  val secret_information: Secret<SecretInformationConfig>,
  val secret_api_key: Secret<String>,
  val secret_number: Secret<Int>,
  val secrets_list: Secret<List<SecretInformationConfig>>,
  val secrets_list_map: Secret<List<Map<String, Int>>>,
  val nested_secret: Secret<NestedSecretConfig>,
  val secret_information_wrapper: SecretInformationWrapperConfig,
  val secret_string: Secret<String>,
  val secret_bytearray: Secret<ByteArray>
) : Config

data class NestedSecretConfig(val nested_nested: SecretInformationWrapperConfig) : Config

data class SecretInformationWrapperConfig(val secret_information: Secret<SecretInformationConfig>) :
    Config

data class SecretInformationConfig(
  val answer_to_universe: String,
  val limit: Int
) : Config

// These are examples of how NOT to define secrets.
data class SelfSecretConfigRoot(
  override var value: SelfSecretConfigRoot
) : Secret<SelfSecretConfigRoot>, Config

data class SecretConfigRoot(
  override var value: SecretInformationConfig
) : Secret<SecretInformationConfig>, Config
