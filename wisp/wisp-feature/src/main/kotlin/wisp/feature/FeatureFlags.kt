package wisp.feature

import java.util.concurrent.Executor

/**
 * Interface for evaluating feature flags.
 */
interface FeatureFlags : StrongFeatureFlags, LegacyFeatureFlags

interface StrongFeatureFlags {
  /**
   * Calculates the value of a boolean feature flag
   *
   * @param flag the feature flag to evaluate
   * @throws [RuntimeException] if the service is unavailable.
   */
  fun get(flag: BooleanFeatureFlag): Boolean

  /**
   * Calculates the value of a string feature flag
   *
   * @param flag the feature flag to evaluate
   * @throws [RuntimeException] if the service is unavailable.
   */
  fun get(flag: StringFeatureFlag): String

  /**
   * Calculates the value of an int feature flag
   *
   * @param flag the feature flag to evaluate
   * @throws [RuntimeException] if the service is unavailable.
   */
  fun get(flag: IntFeatureFlag): Int

  /**
   * Calculates the value of a double feature flag
   *
   * @param flag the feature flag to evaluate
   * @throws [RuntimeException] if the service is unavailable.
   */
  fun get(flag: DoubleFeatureFlag): Double

  /**
   * Calculates the value of an enum feature flag
   *
   * @param flag the feature flag to evaluate
   * @throws [RuntimeException] if the service is unavailable.
   * @throws [IllegalStateException] if the flag is off with no default value.
   */
  fun <T : Enum<T>> get(flag: EnumFeatureFlag<T>): T

  /**
   * Calculates the value of a json feature flag
   *
   * @param flag the feature flag to evaluate
   * @throws [RuntimeException] if the service is unavailable.
   * @throws [IllegalStateException] if the flag is off with no default value.
   */
  fun <T : Any> get(flag: JsonFeatureFlag<T>): T
}

interface LegacyFeatureFlags {
  /**
   * Calculates the value of a boolean feature flag for the given key and attributes.
   * @see [getEnum] for param details
   */
  fun getBoolean(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes()
  ): Boolean

  /**
   * Calculates the value of a double feature flag for the given key and attributes.
   * @see [getEnum] for param details
   */
  fun getDouble(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes()
  ): Double

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
   * Calculates the value of a string feature flag for the given key and attributes.
   * @see [getEnum] for param details
   */
  fun getString(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes()
  ): String

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

  /**
   * Registers a tracker for the value of a boolean feature flag for the given key and attributes.
   * @see [trackEnum] for param details
   */
  fun trackBoolean(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes(),
    executor: Executor,
    tracker: (Boolean) -> Unit
  ): TrackerReference

  /**
   * Registers a tracker for the value of a double feature flag for the given key and attributes.
   * @see [trackEnum] for param details
   */
  fun trackDouble(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes(),
    executor: Executor,
    tracker: (Double) -> Unit
  ): TrackerReference

  /**
   * Registers a tracker for the value of an integer feature flag for the given key and attributes.
   * @see [trackEnum] for param details
   */
  fun trackInt(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes(),
    executor: Executor,
    tracker: (Int) -> Unit
  ): TrackerReference

  /**
   * Registers a tracker for the value of a string feature flag for the given key and attributes.
   * @see [trackEnum] for param details
   */
  fun trackString(
    feature: Feature,
    key: String,
    attributes: Attributes = Attributes(),
    executor: Executor,
    tracker: (String) -> Unit
  ): TrackerReference

  /**
   * Registers a tracker for the value of an enumerated feature flag for the given key and attributes.
   * @param feature name of the feature flag to evaluate.
   * @param key unique primary key for the entity the flag should be evaluated against.
   * @param clazz the enum type.
   * @param attributes additional attributes to provide to flag evaluation.
   * @param tracker a tracker to be registered for processing of changed values
   * @throws [RuntimeException] if the service is unavailable.
   * @throws [IllegalStateException] if the flag is off with no default value.
   * @return a reference to the registered tracker allowing to un-register it
   */
  fun <T : Enum<T>> trackEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes = Attributes(),
    executor: Executor,
    tracker: (T) -> Unit
  ): TrackerReference

  /**
   * Registers a tracker for the value of a JSON feature flag for the given key and attributes.
   *
   * @param clazz the type to convert the JSON string into. It is expected that a Moshi type adapter
   * is registered with the impl.
   * @see [trackEnum] for param details
   */
  fun <T> trackJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes = Attributes(),
    executor: Executor,
    tracker: (T) -> Unit
  ): TrackerReference

  // Overloaded functions for use in Java, because @JvmOverloads isn't supported for interfaces
  fun getBoolean(
    feature: Feature,
    key: String
  ) = getBoolean(feature, key, Attributes())

  fun getDouble(
    feature: Feature,
    key: String
  ) = getDouble(feature, key, Attributes())

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
  ) = getEnum(feature, key, clazz, Attributes())

  fun <T> getJson(
    feature: Feature,
    key: String,
    clazz: Class<T>
  ) = getJson(feature, key, clazz, Attributes())

  fun trackBoolean(
    feature: Feature,
    key: String,
    executor: Executor,
    tracker: (Boolean) -> Unit
  ) = trackBoolean(feature, key, Attributes(), executor, tracker)

  fun trackDouble(
    feature: Feature,
    key: String,
    executor: Executor,
    tracker: (Double) -> Unit
  ) = trackDouble(feature, key, Attributes(), executor, tracker)

  fun trackInt(
    feature: Feature,
    key: String,
    executor: Executor,
    tracker: (Int) -> Unit
  ) = trackInt(feature, key, Attributes(), executor, tracker)

  fun trackString(
    feature: Feature,
    key: String,
    executor: Executor,
    tracker: (String) -> Unit
  ) = trackString(feature, key, Attributes(), executor, tracker)

  fun <T : Enum<T>> trackEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ) = trackEnum(feature, key, clazz, Attributes(), executor, tracker)

  fun <T> trackJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ) = trackJson(feature, key, clazz, Attributes(), executor, tracker)
}

inline fun <reified T : Enum<T>> FeatureFlags.getEnum(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes()
): T = getEnum(feature, key, T::class.java, attributes)

inline fun <reified T> FeatureFlags.getJson(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes()
): T = getJson(feature, key, T::class.java, attributes)

inline fun <reified T : Enum<T>> FeatureFlags.trackEnum(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes(),
  executor: Executor,
  noinline tracker: (T) -> Unit
): TrackerReference = trackEnum(feature, key, T::class.java, attributes, executor, tracker)

inline fun <reified T> FeatureFlags.trackJson(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes(),
  executor: Executor,
  noinline tracker: (T) -> Unit
): TrackerReference = trackJson(feature, key, T::class.java, attributes, executor, tracker)

/**
 * Typed feature string.
 */
open class Feature(val name: String) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Feature) return false

    if (name != other.name) return false

    return true
  }

  override fun hashCode(): Int {
    return name.hashCode()
  }

  override fun toString(): String {
    return "Feature(name='$name')"
  }
}

/**
 * Extra attributes to be used for evaluating features.
 */
open class Attributes @JvmOverloads constructor(
  val text: Map<String, String> = mapOf(),
  // NB: LaunchDarkly uses typed Gson attributes. We could leak that through, but that could make
  // code unwieldly. Numerical attributes are likely to be rarely used, so we make it a separate,
  // optional field rather than trying to account for multiple possible attribute types.
  val number: Map<String, Number>? = null,
  // Indicates that the user is anonymous, which may have backend-specific behavior, like not
  // including the user in analytics.
  val anonymous: Boolean = false
) {
  fun with(name: String, value: String): Attributes =
    copy(text = text.plus(name to value))

  fun with(name: String, value: Number): Attributes {
    val number = number ?: mapOf()
    return copy(number = number.plus(name to value))
  }

  fun copy(
    text: Map<String, String> = this.text,
    number: Map<String, Number>? = this.number,
    anonymous: Boolean = this.anonymous
  ): Attributes = Attributes(text, number, anonymous)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Attributes) return false

    if (text != other.text) return false
    if (number != other.number) return false
    if (anonymous != other.anonymous) return false

    return true
  }

  override fun hashCode(): Int {
    var result = text.hashCode()
    result = 31 * result + (number?.hashCode() ?: 0)
    result = 31 * result + anonymous.hashCode()
    return result
  }

  override fun toString(): String {
    return "Attributes(text=$text, number=$number, anonymous=$anonymous)"
  }
}
