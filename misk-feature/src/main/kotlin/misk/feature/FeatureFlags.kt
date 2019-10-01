package misk.feature

/**
 * Interface for evaluating feature flags.
 */
interface FeatureFlags {

  /**
   * Calculates the value of an boolean feature flag for the given token and attributes.
   * @see [getEnum] for param details
   */
  fun getBool(
    feature: Feature,
    token: String,
    attrs: Attrs? = null
  ): Boolean

  /**
   * Calculates the value of an integer feature flag for the given token and attributes.
   * @see [getEnum] for param details
   */
  fun getInt(
    feature: Feature,
    token: String,
    attrs: Attrs? = null
  ): Int

  /**
   * Calculates the value of a string feature flag for the given token and attributes.
   * @see [getEnum] for param details
   */
  fun getString(
    feature: Feature,
    token: String,
    attrs: Attrs? = null
  ): String

  /**
   * Calculates the value of an enumerated feature flag for the given token and attributes.
   * @param feature name of the feature flag to evaluate.
   * @param token unique primary token for the entity the flag should be evaluated against.
   * @param default default value to return if there was an error evaluating the flag or the flag
   *   does not exist.
   * @param attrs additional attributes to provide to flag evaluation.
   */
  fun <T : Enum<T>> getEnum(
    feature: Feature,
    token: String,
    clazz: Class<T>,
    attrs: Attrs? = null
  ): T
}

inline fun <reified T : Enum<T>> FeatureFlags.getEnum(
  feature: Feature,
  token: String,
  attrs: Attrs? = null
): T = getEnum(feature, token, T::class.java, attrs)

/**
 * Typed feature string.
 */
data class Feature(val name: String)

/**
 * Extra attributes to be used for evaluating features.
 */
data class Attrs(
  val stringAttrs: Map<String, String> = mapOf(),
  // NB: LaunchDarkly uses typed Gson attributes. We could leak that through, but that could make
  // code unwieldly. Numerical attributes are likely to be rarely used, so we make it a separate,
  // optional field rather than trying to account for multiple possible attribute types.
  val numberAttrs: Map<String, Number>? = null
)
