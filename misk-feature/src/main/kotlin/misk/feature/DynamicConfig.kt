package misk.feature

import java.util.concurrent.Executor

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

  /**
   * Registers a boolean dynamic config tracker which will be invoked whenever the boolean
   * dynamic config changes value.
   *
   * Returns a tracker reference which can be used to un-register the tracker.
   */
  fun trackBoolean(feature: Feature, executor: Executor, tracker: (Boolean) -> Unit): TrackerReference

  /**
   * Registers a integer dynamic config tracker which will be invoked whenever the integer
   * dynamic config changes value.
   *
   * Returns a tracker reference which can be used to un-register the tracker.
   */
  fun trackInt(feature: Feature, executor: Executor, tracker: (Int) -> Unit): TrackerReference

  /**
   * Registers a string dynamic config tracker which will be invoked whenever the string
   * dynamic config changes value.
   *
   * Returns a tracker reference which can be used to un-register the tracker.
   */
  fun trackString(feature: Feature, executor: Executor, tracker: (String) -> Unit): TrackerReference

  /**
   * Registers a enum dynamic config tracker which will be invoked whenever the enum
   * dynamic config changes value.
   *
   * Returns a tracker reference which can be used to un-register the tracker.
   */
  fun <T: Enum<T>>trackEnum(feature: Feature, clazz: Class<T>, executor: Executor, tracker: (T) -> Unit): TrackerReference

  /**
   * Registers a json dynamic config tracker which will be invoked whenever the json
   * dynamic config changes value.
   *
   * Returns a tracker reference which can be used to un-register the tracker.
   */
  fun <T> trackJson(feature: Feature, clazz: Class<T>, executor: Executor, tracker: (T) -> Unit): TrackerReference
}

inline fun <reified T : Enum<T>> DynamicConfig.getEnum(
  feature: Feature
): T = getEnum(feature, T::class.java)

inline fun <reified T : Enum<T>> DynamicConfig.trackEnum(
  feature: Feature,
  executor: Executor,
  noinline tracker: (T) -> Unit
): TrackerReference = trackEnum(feature, T::class.java, executor, tracker)

inline fun <reified T> DynamicConfig.getJson(feature: Feature): T = getJson(feature, T::class.java)

inline fun <reified T> DynamicConfig.trackJson(
  feature: Feature,
  executor: Executor,
  noinline tracker: (T) -> Unit
): TrackerReference = trackJson(feature, T::class.java, executor, tracker)
