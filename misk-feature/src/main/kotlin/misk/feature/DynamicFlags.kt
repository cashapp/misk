package misk.feature

/**
 * Interface for evaluating dynamic flags. Dynamic flags are similar to feature flags, but they
 * don't support different variations for different keys. Implementations may choose to use a
 * relatively stable key like request ID to keep the value consistent across multiple calls in
 * the same request.
 */
interface DynamicFlags {
  /**
   * Returns the value of an boolean dynamic flag.
   */
  fun getBoolean(
    feature: Feature
  ): Boolean

  /**
   * Returns the value of an integer dynamic flag.
   */
  fun getInt(
    feature: Feature
  ): Int

  /**
   * Returns the value of a string dynamic flag.
   */
  fun getString(
    feature: Feature
  ): String

  /**
   * Returns the value of an enumerated dynamic flag.
   */
  fun <T : Enum<T>> getEnum(
    feature: Feature
  ): T
}

inline fun <reified T : Enum<T>> DynamicFlags.getEnum(
  feature: Feature
): T = getEnum(feature)
