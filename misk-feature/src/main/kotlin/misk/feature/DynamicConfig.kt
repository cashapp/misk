package misk.feature

/**
 * Interface for evaluating dynamic flags. Dynamic flags are similar to feature flags, but they
 * don't support different variations for different keys.
 */
interface DynamicConfig {
  /**
   * Returns the value of an boolean dynamic flag.
   */
  fun getBoolean(feature: Feature): Boolean

  /**
   * Returns the value of an integer dynamic flag.
   */
  fun getInt(feature: Feature): Int

  /**
   * Returns the value of a string dynamic flag.
   */
  fun getString(feature: Feature): String

  /**
   * Returns the value of an enumerated dynamic flag.
   */
  fun <T : Enum<T>> getEnum(feature: Feature, clazz: Class<T>): T

  /**
   * Returns the value of a JSON dynamic flag.
   */
  fun <T> getJson(feature: Feature, clazz: Class<T>): T
}

inline fun <reified T : Enum<T>> DynamicConfig.getEnum(
  feature: Feature
): T = getEnum(feature, T::class.java)

inline fun <reified T> DynamicConfig.getJson(feature: Feature): T = getJson(feature, T::class.java)
