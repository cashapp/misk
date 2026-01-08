package wisp.launchdarkly

import com.google.common.base.Preconditions.checkState
import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.EvaluationReason
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.server.interfaces.LDClientInterface
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.system.measureTimeMillis
import mu.KotlinLogging
import wisp.feature.Attributes
import wisp.feature.BooleanFeatureFlag
import wisp.feature.DoubleFeatureFlag
import wisp.feature.EnumFeatureFlag
import wisp.feature.Feature
import wisp.feature.FeatureFlagValidation
import wisp.feature.FeatureFlags
import wisp.feature.IntFeatureFlag
import wisp.feature.JsonFeatureFlag
import wisp.feature.StringFeatureFlag
import wisp.feature.TrackerReference
import wisp.feature.fromSafeJson

/**
 * Implementation of [FeatureFlags] using LaunchDarkly's Java SDK. See
 * https://docs.launchdarkly.com/docs/java-sdk-reference documentation.
 */
class LaunchDarklyFeatureFlags
@JvmOverloads
constructor(
  private val ldClient: Lazy<LDClientInterface>,
  private val moshi: Moshi,
  meterRegistry: MeterRegistry = Metrics.globalRegistry,
) : FeatureFlags {
  @Deprecated("Only here for binary compatibility.")
  @JvmOverloads
  constructor(
    ldClient: LDClientInterface,
    moshi: Moshi,
    meterRegistry: MeterRegistry = Metrics.globalRegistry,
  ) : this(lazy { ldClient }, moshi, meterRegistry)

  private var featuresWithMigrationWarnings: MutableList<Feature> = mutableListOf()
  private val launchDarklyClientMetrics = LaunchDarklyClientMetrics(meterRegistry)

  fun startUp(): LaunchDarklyFeatureFlags {
    val timedResult = measureTimeMillis {
      var attempts = 300
      val intervalMillis = 100L

      // LaunchDarkly has its own threads for initialization. We just need to keep checking until
      // it's done. Unfortunately there's no latch or event to wait on.
      while (!ldClient.value.isInitialized && attempts > 0) {
        Thread.sleep(intervalMillis)
        attempts--
      }

      if (attempts == 0 && !ldClient.value.isInitialized) {
        launchDarklyClientMetrics.onInitFailure()
        val errorMessage = "LaunchDarkly did not initialize in 30 seconds"
        logger.error { errorMessage }
        throw Exception(errorMessage)
      }
    }
    launchDarklyClientMetrics.onInitSuccess(timedResult)
    logger.info { "LaunchDarkly successfully initialized in $timedResult ms" }
    return this
  }

  fun shutDown() {
    ldClient.value.flush()
    ldClient.value.close()
  }

  private fun <T> get(
    feature: Feature,
    key: String,
    attributes: Attributes,
    callLdVariation: (String, LDContext) -> EvaluationDetail<T>,
  ): T {
    checkInitialized()
    val result = callLdVariation(feature.name, buildContext(feature, key, attributes))
    checkDefaultNotUsed(feature, result)
    return result.value
  }

  override fun get(flag: BooleanFeatureFlag): Boolean = getBoolean(flag.feature, flag.key, flag.attributes)

  override fun get(flag: StringFeatureFlag): String = getString(flag.feature, flag.key, flag.attributes)

  override fun get(flag: IntFeatureFlag): Int = getInt(flag.feature, flag.key, flag.attributes)

  override fun get(flag: DoubleFeatureFlag): Double = getDouble(flag.feature, flag.key, flag.attributes)

  override fun <T : Enum<T>> get(flag: EnumFeatureFlag<T>): T {
    return getEnum(flag.feature, flag.key, flag.returnType, flag.attributes)
  }

  override fun <T : Any> get(flag: JsonFeatureFlag<T>): T =
    getJson(flag.feature, flag.key, flag.returnType, flag.attributes)

  override fun getBoolean(feature: Feature, key: String, attributes: Attributes): Boolean =
    get(feature, key, attributes) { name, context -> ldClient.value.boolVariationDetail(name, context, false) }

  override fun getDouble(feature: Feature, key: String, attributes: Attributes): Double =
    get(feature, key, attributes) { name, context -> ldClient.value.doubleVariationDetail(name, context, 0.0) }

  override fun getInt(feature: Feature, key: String, attributes: Attributes): Int =
    get(feature, key, attributes) { name, context -> ldClient.value.intVariationDetail(name, context, 0) }

  override fun getString(feature: Feature, key: String, attributes: Attributes): String =
    get(feature, key, attributes) { name, context -> ldClient.value.stringVariationDetail(name, context, "") }

  override fun <T : Enum<T>> getEnum(feature: Feature, key: String, clazz: Class<T>, attributes: Attributes): T {
    val result =
      get(feature, key, attributes) { name, context -> ldClient.value.stringVariationDetail(name, context, "") }
    return java.lang.Enum.valueOf(clazz, result.uppercase(Locale.getDefault()))
  }

  override fun <T> getJson(feature: Feature, key: String, clazz: Class<T>, attributes: Attributes): T {
    val result =
      get(feature, key, attributes) { name, context ->
        ldClient.value.jsonValueVariationDetail(name, context, LDValue.ofNull())
      }
    return moshi.adapter(clazz).fromSafeJson(result.toJsonString()) { exception ->
      logJsonMigrationWarningOnce(feature, exception)
    } ?: throw IllegalArgumentException("null value deserialized from $feature")
  }

  override fun getJsonString(feature: Feature, key: String, attributes: Attributes): String {
    val result =
      get(feature, key, attributes) { name, context ->
        ldClient.value.jsonValueVariationDetail(name, context, LDValue.ofNull())
      }
    return result.toJsonString()
  }

  private fun <T> track(
    feature: Feature,
    key: String,
    attributes: Attributes,
    mapper: (LDValue) -> T,
    executor: Executor,
    tracker: (T) -> Unit,
  ): TrackerReference {
    checkInitialized()
    val listener =
      ldClient.value.flagTracker.addFlagValueChangeListener(feature.name, buildContext(feature, key, attributes)) {
        event ->
        executor.execute { tracker(mapper(event.newValue)) }
      }

    return object : TrackerReference {
      override fun unregister() {
        ldClient.value.flagTracker.removeFlagChangeListener(listener)
      }
    }
  }

  override fun trackBoolean(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Boolean) -> Unit,
  ) = track(feature, key, attributes, { it.booleanValue() }, executor, tracker)

  override fun trackDouble(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Double) -> Unit,
  ) = track(feature, key, attributes, { it.doubleValue() }, executor, tracker)

  override fun trackInt(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Int) -> Unit,
  ) = track(feature, key, attributes, { it.intValue() }, executor, tracker)

  override fun trackString(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (String) -> Unit,
  ) = track(feature, key, attributes, { it.stringValue() }, executor, tracker)

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit,
  ) =
    track(
      feature,
      key,
      attributes,
      { java.lang.Enum.valueOf(clazz, it.stringValue().uppercase(Locale.getDefault())) },
      executor,
      tracker,
    )

  override fun <T> trackJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit,
  ) =
    track(
      feature,
      key,
      attributes,
      {
        moshi.adapter(clazz).fromSafeJson(it.toJsonString()) { exception ->
          logJsonMigrationWarningOnce(feature, exception)
        }!!
      },
      executor,
      tracker,
    )

  private fun checkInitialized() {
    checkState(ldClient.value.isInitialized, "LaunchDarkly feature flags not initialized.")
  }

  private fun <T> checkDefaultNotUsed(feature: Feature, detail: EvaluationDetail<T>) {
    if (!detail.isDefaultValue) {
      return
    }

    // if we're in offline mode, using the default value if ok
    if (ldClient.value.isOffline) {
      return
    }

    if (detail.reason.kind == EvaluationReason.Kind.ERROR) {
      val reason = detail.reason
      throw RuntimeException("Feature flag $feature evaluation failed: ${reason}", reason.exception)
    }

    throw IllegalStateException(
      "Feature flag $feature is off but no off variation is specified, evaluation reason: ${detail.reason}"
    )
  }

  private fun buildContext(feature: Feature, key: String, attributes: Attributes): LDContext {
    FeatureFlagValidation.checkValidKey(feature, key)
    val builder = LDContext.builder(key)
    attributes.text.forEach { (k, v) ->
      when (k) {
        // Deprecated LDUser had these non-private built in attributes:
        "ip" -> builder.set(k, v)
        "email" -> builder.set(k, v)
        "name" -> builder.set(k, v)
        "avatar" -> builder.set(k, v)
        "firstName" -> builder.set(k, v)
        "lastName" -> builder.set(k, v)
        "country" -> builder.set(k, v)
        else -> builder.set(k, v).privateAttributes(k)
      }
    }
    if (attributes.number != null) {
      attributes.number!!.forEach { (k, v) ->
        when (v) {
          is Long -> {
            builder.set(k, LDValue.of(v)).privateAttributes(k)
          }

          is Int -> {
            builder.set(k, LDValue.of(v)).privateAttributes(k)
          }

          is Double -> {
            builder.set(k, LDValue.of(v)).privateAttributes(k)
          }

          is Float -> {
            builder.set(k, LDValue.of(v)).privateAttributes(k)
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

      logger.warn(exception) { "failed to parse JSON due to unknown fields. ignoring those fields and trying again" }
    }
  }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
