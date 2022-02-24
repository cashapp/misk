package wisp.feature.testing

import com.squareup.moshi.Moshi
import wisp.config.Config
import wisp.config.Configurable
import wisp.feature.Attributes
import wisp.feature.BooleanFeatureFlag
import wisp.feature.DoubleFeatureFlag
import wisp.feature.DynamicConfig
import wisp.feature.EnumFeatureFlag
import wisp.feature.Feature
import wisp.feature.FeatureFlag
import wisp.feature.FeatureFlags
import wisp.feature.IntFeatureFlag
import wisp.feature.JsonFeatureFlag
import wisp.feature.LegacyFeatureFlags
import wisp.feature.StringFeatureFlag
import wisp.feature.StrongFeatureFlags
import wisp.moshi.defaultKotlinMoshi
import kotlin.reflect.KClass

/**
 * In-memory test implementation of [FeatureFlags] that allows flags to be overridden.
 */
class FakeFeatureFlags private constructor(
  val legacyFeatureFlags: FakeLegacyFeatureFlags,
  val strongFeatureFlags: FakeStrongFeatureFlags
) : FeatureFlags,
  LegacyFeatureFlags by legacyFeatureFlags,
  DynamicConfig by legacyFeatureFlags,
  StrongFeatureFlags by strongFeatureFlags,
  Configurable<FakeFeatureFlagsConfig> {

  companion object {
    const val KEY = "fake_dynamic_flag"
    val defaultAttributes = Attributes()
  }

  @Deprecated("Needed for Misk Provider usage...")
  @Suppress("DEPRECATION")
  constructor(
    moshi: () -> Moshi,
  ) : this(FakeLegacyFeatureFlags(moshi), FakeStrongFeatureFlags())

  /**
   * Preferred constructor for Wisp
   */
  @Suppress("DEPRECATION")
  constructor(
    moshi: Moshi = defaultKotlinMoshi
  ) : this({ moshi })

  fun <T : Any, Flag : FeatureFlag<in T>> overrideAny(
    clazz: Class<out FeatureFlag<T>>,
    value: T,
    matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = this.also { strongFeatureFlags.overrideAny(clazz, value, matcher) }

  inline fun <reified Flag : BooleanFeatureFlag> override(
    value: Boolean,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = this.also { strongFeatureFlags.override(value, matcher) }

  inline fun <reified Flag : StringFeatureFlag> override(
    value: String,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = this.also { strongFeatureFlags.override(value, matcher) }

  inline fun <reified Flag: IntFeatureFlag> override(
    value: Int,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = this.also { strongFeatureFlags.override(value, matcher) }

  inline fun <reified Flag: DoubleFeatureFlag> override(
    value: Double,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = this.also { strongFeatureFlags.override(value, matcher) }

  inline fun <reified Flag: JsonFeatureFlag<T>, T : Any> override(
    value: T,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = this.also { strongFeatureFlags.override(value, matcher) }

  inline fun <reified Flag: EnumFeatureFlag<T>, T : Enum<T>> override(
    value: T,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeFeatureFlags = this.also { strongFeatureFlags.override(value, matcher) }

  fun override(feature: Feature, value: Boolean) = legacyFeatureFlags.override(feature, value)
  fun override(feature: Feature, value: Double) = legacyFeatureFlags.override(feature, value)
  fun override(feature: Feature, value: Int) = legacyFeatureFlags.override(feature, value)
  fun override(feature: Feature, value: String) = legacyFeatureFlags.override(feature, value)
  fun override(feature: Feature, value: Enum<*>) = legacyFeatureFlags.override(feature, value)
  fun <T> override(feature: Feature, value: T) = legacyFeatureFlags.override(feature, value)
  fun <T> override(feature: Feature, value: T, clazz: Class<T>) =
    legacyFeatureFlags.override(feature, value, clazz)

  fun overrideJsonString(feature: Feature, json: String) =
    legacyFeatureFlags.overrideJsonString(feature, json)

  inline fun <reified T> overrideJson(feature: Feature, value: T) =
    overrideKeyJson(feature, KEY, value, defaultAttributes)

  @JvmOverloads
  fun overrideKey(
    feature: Feature, key: String, value: Boolean, attributes: Attributes = defaultAttributes
  ) = legacyFeatureFlags.overrideKey(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(
    feature: Feature,
    key: String,
    value: Double,
    attributes: Attributes = defaultAttributes
  ) = legacyFeatureFlags.overrideKey(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(
    feature: Feature,
    key: String,
    value: Int,
    attributes: Attributes = defaultAttributes
  ) = legacyFeatureFlags.overrideKey(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(
    feature: Feature,
    key: String,
    value: String,
    attributes: Attributes = defaultAttributes
  ) = legacyFeatureFlags.overrideKey(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(
    feature: Feature,
    key: String,
    value: Enum<*>,
    attributes: Attributes = defaultAttributes
  ) = legacyFeatureFlags.overrideKey(feature, key, value, attributes)

  fun <T> overrideKey(
    feature: Feature,
    key: String,
    value: T,
    clazz: Class<T>
  ) = legacyFeatureFlags.overrideKey(feature, key, value, clazz)

  @JvmOverloads
  fun <T> overrideKey(
    feature: Feature,
    key: String,
    value: T,
    attributes: Attributes = defaultAttributes
  ) = legacyFeatureFlags.overrideKey(feature, key, value, attributes)

  @JvmOverloads
  inline fun <reified T> overrideKeyJson(
    feature: Feature,
    key: String,
    value: T,
    attributes: Attributes = defaultAttributes
  ) = legacyFeatureFlags.overrideKeyJson(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKeyJsonString(
    feature: Feature,
    key: String,
    value: String,
    attributes: Attributes = defaultAttributes
  ) {
    overrideKey(feature, key, { value }, attributes)
  }

  fun reset() {
    legacyFeatureFlags.reset()
    strongFeatureFlags.reset()
  }

  /**
   * Configures the feature flags values from supplied config.
   */
  override fun configure(config: FakeFeatureFlagsConfig) {
    config.featuresConfig.forEach {
      val key = it.key ?: KEY

      when (it.type) {
        "Boolean" -> overrideKey(
          Feature(it.featureName),
          key,
          it.value.toBoolean(),
          it.attributes
        )
        "Int" -> overrideKey(Feature(it.featureName), key, it.value.toInt(), it.attributes)
        "Double" -> overrideKey(
          Feature(it.featureName),
          key,
          it.value.toDouble(),
          it.attributes
        )
        "String" -> overrideKey(Feature(it.featureName), key, it.value, it.attributes)
        "ENUM" -> {
          TODO(
            "Need to work out how to dynamically create an Enum of type X " +
              "with the String value"
          )
        }
        "JSON" -> overrideKeyJsonString(
          Feature(it.featureName),
          key,
          it.value,
          it.attributes
        )
        else -> {
          // unknown type 'T' for the value, pass it on
          TODO("Need a way to convert from String to unknown type 'T'")
        }
      }
    }
  }

  override fun getConfigClass(): KClass<FakeFeatureFlagsConfig> {
    return FakeFeatureFlagsConfig::class
  }

}

data class FeaturesConfig(
  val featureName: String,
  val key: String? = null,
  val attributes: Attributes = Attributes(),
  val value: String,
  val type: String = "String"
)

data class FakeFeatureFlagsConfig(
  val featuresConfig: List<FeaturesConfig> = emptyList()
) : Config
