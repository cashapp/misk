package misk.feature

/**
 * Interface for evaluating feature flags.
 */
interface FeatureFlags {

  /**
   * Calculates the value of an boolean feature flag for the given key and attributes.
   * @see [getEnum] for param details
   */
  fun getBoolean(
    feature: Feature,
    key: String,
    attributes: Attributes
  ): Boolean

  /**
   * Calculates the value of an integer feature flag for the given key and attributes.
   * @see [getEnum] for param details
   */
  fun getInt(
    feature: Feature,
    key: String,
    attributes: Attributes
  ): Int

  /**
   * Calculates the value of a string feature flag for the given key and attributes.
   * @see [getEnum] for param details
   */
  fun getString(
    feature: Feature,
    key: String,
    attributes: Attributes
  ): String

  /**
   * Calculates the value of an enumerated feature flag for the given key and attributes.
   * @param feature name of the feature flag to evaluate.
   * @param key unique primary key for the entity the flag should be evaluated against.
   * @param default default value to return if there was an error evaluating the flag or the flag
   *   does not exist.
   * @param attributes additional attributes to provide to flag evaluation.
   */
  fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T

  // Overloaded functions for use in Java, because @JvmOverloads isn't supported for interfaces
  fun getBoolean(
    feature: Feature,
    key: String
  ) = getBoolean(feature, key, Attributes())

  fun getInt(
    feature: Feature,
    key: String
  ) = getInt(feature, key, Attributes())

  fun getString(
    feature: Feature,
    key: String
  ) = getString(feature, key, Attributes())

  fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>
  ) = getString(feature, key, Attributes())
}

inline fun <reified T : Enum<T>> FeatureFlags.getEnum(
  feature: Feature,
  token: String,
  attributes: Attributes = Attributes()
): T = getEnum(feature, token, T::class.java, attributes)

/**
 * Typed feature string.
 */
data class Feature(val name: String)

/**
 * Extra attributes to be used for evaluating features.
 */
data class Attributes(
  val text: Map<String, String> = mapOf(),
  // NB: LaunchDarkly uses typed Gson attributes. We could leak that through, but that could make
  // code unwieldly. Numerical attributes are likely to be rarely used, so we make it a separate,
  // optional field rather than trying to account for multiple possible attribute types.
  val number: Map<String, Number>? = null,
  // Indicates that the user is anonymous, which may have backend-specific behavior, like not
  // including the user in analytics.
  val anonymous: Boolean = false
)
