package misk.config

/**
 * Type for any secrets that need to be loaded as reference in their config file.
 * Secret addresses should use format specified in [misk.resources.ResourceLoader].
 *
 * Usage example:
 * ```
 * data class SuperSecretConfig(
 *  val string_value: String,
 *  val secret_information: Secret<SecretInformationConfig>,
 * ) : Config
 * ```
 *
 * With
 * ```
 * data class SecretInformationConfig(
 *  val answer_to_universe: String,
 *  val limit: Int
 * ) : Config
 * ```
 *
 * We would have the SuperSecretConfig yaml be:
 * ```
 * string_value: "this is not a secret"
 * secret_information: "classpath:/misk/resources/secret_information_values.yaml"
 * ```
 *
 * And the secret_information stored as:
 * ```
 * answer_to_universe: 42
 * limit: 5
 * ```
 *
 * Lastly, this secret information would be accessed using:
 * ```
 * superSecretConfig.secret_information.value.answer_to_universe
 * ```
 */

interface Secret<T> {
  val value: T
}
