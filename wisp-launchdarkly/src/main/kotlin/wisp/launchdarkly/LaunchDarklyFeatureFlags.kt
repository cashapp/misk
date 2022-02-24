package wisp.launchdarkly

import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.EvaluationReason
import com.launchdarkly.sdk.LDUser
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.server.interfaces.LDClientInterface
import com.launchdarkly.shaded.com.google.common.base.Preconditions.checkState
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import wisp.feature.FeatureFlagValidation
import mu.KotlinLogging
import wisp.feature.Attributes
import wisp.feature.Feature
import wisp.feature.FeatureFlags
import wisp.feature.BooleanFeatureFlag
import wisp.feature.StringFeatureFlag
import wisp.feature.IntFeatureFlag
import wisp.feature.DoubleFeatureFlag
import wisp.feature.EnumFeatureFlag
import wisp.feature.JsonFeatureFlag
import wisp.feature.TrackerReference
import wisp.feature.fromSafeJson
import java.util.concurrent.Executor

/**
 * Implementation of [FeatureFlags] using LaunchDarkly's Java SDK.
 * See https://docs.launchdarkly.com/docs/java-sdk-reference documentation.
 */
class LaunchDarklyFeatureFlags constructor(
  private val ldClient: LDClientInterface,
  private val moshi: Moshi
) : FeatureFlags {

  private var featuresWithMigrationWarnings: MutableList<Feature> = mutableListOf()

  fun startUp(): LaunchDarklyFeatureFlags {
    var attempts = 300
    val intervalMillis = 100L

    // LaunchDarkly has its own threads for initialization. We just need to keep checking until
    // it's done. Unfortunately there's no latch or event to wait on.
    while (!ldClient.isInitialized && attempts > 0) {
      Thread.sleep(intervalMillis)
      attempts--
    }

    if (attempts == 0 && !ldClient.isInitialized) {
      throw Exception("LaunchDarkly did not initialize in 30 seconds")
    }

    return this
  }

  fun shutDown() {
    ldClient.flush()
    ldClient.close()
  }

  private fun <T> get(
    feature: Feature,
    key: String,
    attributes: Attributes,
    callLdVariation: (String, LDUser) -> EvaluationDetail<T>
  ): T {
    checkInitialized()
    val result = callLdVariation(
      feature.name,
      buildUser(feature, key, attributes),
    )
    checkDefaultNotUsed(feature, result)
    return result.value
  }

  override fun get(flag: BooleanFeatureFlag): Boolean =
    getBoolean(flag.feature, flag.key, flag.attributes)

  override fun get(flag: StringFeatureFlag): String =
    getString(flag.feature, flag.key, flag.attributes)

  override fun get(flag: IntFeatureFlag): Int =
    getInt(flag.feature, flag.key, flag.attributes)

  override fun get(flag: DoubleFeatureFlag): Double =
    getDouble(flag.feature, flag.key, flag.attributes)

  override fun <T : Enum<T>> get(flag: EnumFeatureFlag<T>): T =
    getEnum(flag.feature, flag.key, flag.returnType, flag.attributes)

  override fun <T : Any> get(flag: JsonFeatureFlag<T>): T =
    getJson(flag.feature, flag.key, flag.returnType, flag.attributes)

  override fun getBoolean(feature: Feature, key: String, attributes: Attributes): Boolean =
    get(feature, key, attributes) { name, user ->
      ldClient.boolVariationDetail(name, user, false)
    }

  override fun getDouble(feature: Feature, key: String, attributes: Attributes): Double =
    get(feature, key, attributes) { name, user ->
      ldClient.doubleVariationDetail(name, user, 0.0)
    }

  override fun getInt(feature: Feature, key: String, attributes: Attributes): Int =
    get(feature, key, attributes) { name, user ->
      ldClient.intVariationDetail(name, user, 0)
    }

  override fun getString(feature: Feature, key: String, attributes: Attributes): String =
    get(feature, key, attributes) { name, user ->
      ldClient.stringVariationDetail(name, user, "")
    }

  override fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    val result = get(feature, key, attributes) { name, user ->
      ldClient.stringVariationDetail(name, user, "")
    }
    return java.lang.Enum.valueOf(clazz, result.toUpperCase())
  }

  override fun <T> getJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    val result = get(feature, key, attributes) { name, user ->
      ldClient.jsonValueVariationDetail(name, user, LDValue.ofNull())
    }
    return moshi.adapter(clazz)
      .fromSafeJson(result.toJsonString()) { exception ->
        logJsonMigrationWarningOnce(feature, exception)
      }
      ?: throw IllegalArgumentException("null value deserialized from $feature")
  }

  private fun <T> track(
    feature: Feature,
    key: String,
    attributes: Attributes,
    mapper: (LDValue) -> T,
    executor: Executor,
    tracker: (T) -> Unit
  ): TrackerReference {
    checkInitialized()
    val listener = ldClient.flagTracker.addFlagValueChangeListener(
      feature.name,
      buildUser(feature, key, attributes)
    ) { event ->
      executor.execute {
        tracker(mapper(event.newValue))
      }
    }

    return object : TrackerReference {
      override fun unregister() {
        ldClient.flagTracker.removeFlagChangeListener(listener)
      }
    }
  }

  override fun trackBoolean(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Boolean) -> Unit
  ) = track(feature, key, attributes, { it.booleanValue() }, executor, tracker)

  override fun trackDouble(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Double) -> Unit
  ) = track(feature, key, attributes, { it.doubleValue() }, executor, tracker)

  override fun trackInt(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Int) -> Unit
  ) = track(feature, key, attributes, { it.intValue() }, executor, tracker)

  override fun trackString(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (String) -> Unit
  ) = track(feature, key, attributes, { it.stringValue() }, executor, tracker)

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit
  ) = track(
    feature,
    key,
    attributes,
    { java.lang.Enum.valueOf(clazz, it.stringValue().toUpperCase()) },
    executor,
    tracker
  )

  override fun <T> trackJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit
  ) = track(
    feature,
    key,
    attributes,
    {
      moshi.adapter(clazz).fromSafeJson(it.toJsonString()) { exception ->
        logJsonMigrationWarningOnce(feature, exception)
      }!!
    },
    executor,
    tracker
  )

  private fun checkInitialized() {
    checkState(
      ldClient.isInitialized,
      "LaunchDarkly feature flags not initialized."
    )
  }

  private fun <T> checkDefaultNotUsed(feature: Feature, detail: EvaluationDetail<T>) {
    if (!detail.isDefaultValue) {
      return
    }

    // if we're in offline mode, using the default value if ok
    if (ldClient.isOffline) {
      return
    }

    if (detail.reason.kind == EvaluationReason.Kind.ERROR) {
      val reason = detail.reason
      throw RuntimeException(
        "Feature flag $feature evaluation failed: ${reason}", reason.exception
      )
    }

    throw IllegalStateException("Feature flag $feature is off but no off variation is specified")
  }

  private fun buildUser(feature: Feature, key: String, attributes: Attributes): LDUser {
    FeatureFlagValidation.checkValidKey(feature, key)
    val builder = LDUser.Builder(key)
    attributes.text.forEach { (k, v) ->
      when (k) {
        // LaunchDarkly has some built-in keys that have to be initialized with their named
        // methods.
        "secondary" -> builder.secondary(v)
        "ip" -> builder.ip(v)
        "email" -> builder.email(v)
        "name" -> builder.name(v)
        "avatar" -> builder.avatar(v)
        "firstName" -> builder.firstName(v)
        "lastName" -> builder.lastName(v)
        "country" -> builder.country(v)
        else -> builder.privateCustom(k, v)
      }
    }
    if (attributes.number != null) {
      attributes.number!!.forEach { (k, v) ->
        when (v) {
          is Long -> {
            builder.privateCustom(k, LDValue.of(v))
          }
          is Int -> {
            builder.privateCustom(k, LDValue.of(v))
          }
          is Double -> {
            builder.privateCustom(k, LDValue.of(v))
          }
          is Float -> {
            builder.privateCustom(k, LDValue.of(v))
          }
        }
      }
    }
    if (attributes.anonymous) {
      // This prevents the user from being stored in the LaunchDarkly dashboard, see
      // https://docs.launchdarkly.com/docs/anonymous-users
      builder.anonymous(true)
    }
    return builder.build()
  }

  private fun logJsonMigrationWarningOnce(feature: Feature, exception: JsonDataException) {
    if (!featuresWithMigrationWarnings.contains(feature)) {
      featuresWithMigrationWarnings.add(feature)

      logger.warn(exception) {
        "failed to parse JSON due to unknown fields. ignoring those fields and trying again"
      }
    }
  }

  companion object {
    val logger = KotlinLogging.logger {}
  }
}
