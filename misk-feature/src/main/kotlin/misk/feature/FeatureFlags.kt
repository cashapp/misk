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
    attributes: Attributes = Attributes()
  ): Boolean

  /**
   * Calculates the value of an integer feature flag for the given key and attributes.
   * @see [getEnum] for param details
   */
  fun getInt(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes()
  ): Int

  /**
   * Calculates the value of an integer feature flag for the given key and attributes, or returns
   * null if the flag is set to nothing.
   * @see [getEnum] for param details
   */
  fun getIntOrNull(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes()
  ): Int?

  /**
   * Calculates the value of a string feature flag for the given key and attributes.
   * @see [getEnum] for param details
   */
  fun getString(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes()
  ): String

  /**
   * Calculates the value of a string feature flag for the given key and attributes, or returns
   * null if the flag is set to nothing.
   * @see [getEnum] for param details
   */
  fun getStringOrNull(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes()
  ): String?

  /**
   * Calculates the value of an enumerated feature flag for the given key and attributes.
   * @param feature name of the feature flag to evaluate.
   * @param key unique primary key for the entity the flag should be evaluated against.
   * @param clazz the enum type.
   * @param attributes additional attributes to provide to flag evaluation.
   * @throws [RuntimeException] if the service is unavailable.
   * @throws [IllegalStateException] if the flag is off with no default value.
   */
  fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes = Attributes()
  ): T

  fun <T : Enum<T>> getEnumOrNull(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes = Attributes()
  ): T?

  /**
   * Calculates the value of a JSON feature flag for the given key and attributes.
   *
   * @param clazz the type to convert the JSON string into. It is expected that a Moshi type adapter
   * is registered with the impl.
   * @see [getEnum] for param details
   */
  fun <T> getJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes = Attributes()
  ): T

  fun <T> getJsonOrNull(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes = Attributes()
  ): T?

  // Overloaded functions for use in Java, because @JvmOverloads isn't supported for interfaces
  fun getBoolean(
    feature: Feature,
    key: String
  ) = getBoolean(feature, key, Attributes())

  fun getInt(
    feature: Feature,
    key: String
  ) = getInt(feature, key, Attributes())

  fun getIntOrNull(
    feature: Feature,
    key: String
  ) = getIntOrNull(feature, key, Attributes())

  fun getString(
    feature: Feature,
    key: String
  ) = getString(feature, key, Attributes())

  fun getStringOrNull(
    feature: Feature,
    key: String
  ) = getStringOrNull(feature, key, Attributes())

  fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>
  ) = getEnum(feature, key, clazz, Attributes())

  fun <T : Enum<T>> getEnumOrNull(
    feature: Feature,
    key: String,
    clazz: Class<T>
  ) = getEnumOrNull(feature, key, clazz, Attributes())

  fun <T> getJson(feature: Feature, key: String, clazz: Class<T>)
      = getJson(feature, key, clazz, Attributes())

  fun <T> getJsonOrNull(feature: Feature, key: String, clazz: Class<T>)
      = getJsonOrNull(feature, key, clazz, Attributes())
}

inline fun <reified T : Enum<T>> FeatureFlags.getEnum(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes()
): T = getEnum(feature, key, T::class.java, attributes)

inline fun <reified T : Enum<T>> FeatureFlags.getEnumOrNull(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes()
): T? = getEnumOrNull(feature, key, T::class.java, attributes)

inline fun <reified T> FeatureFlags.getJson(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes()
): T = getJson(feature, key, T::class.java, attributes)

inline fun <reified T> FeatureFlags.getJsonOrNull(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes()
): T? = getJsonOrNull(feature, key, T::class.java, attributes)

/**
 * Typed feature string.
 */
data class Feature(val name: String)

/**
 * Extra attributes to be used for evaluating features.
 */
data class Attributes @JvmOverloads constructor(
  val text: Map<String, String> = mapOf(),
  // NB: LaunchDarkly uses typed Gson attributes. We could leak that through, but that could make
  // code unwieldly. Numerical attributes are likely to be rarely used, so we make it a separate,
  // optional field rather than trying to account for multiple possible attribute types.
  val number: Map<String, Number>? = null,
  // Indicates that the user is anonymous, which may have backend-specific behavior, like not
  // including the user in analytics.
  val anonymous: Boolean = false
)
