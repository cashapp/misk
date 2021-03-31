package misk.feature.launchdarkly

import com.google.common.util.concurrent.AbstractIdleService
import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.EvaluationReason
import com.launchdarkly.sdk.LDUser
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.server.interfaces.LDClientInterface

import com.launchdarkly.shaded.com.google.common.base.Preconditions.checkState
import com.squareup.moshi.Moshi
import misk.feature.Attributes
import misk.feature.Feature
import misk.feature.FeatureFlagValidation
import misk.feature.FeatureFlags
import misk.feature.FeatureService
import misk.feature.fromSafeJson
import mu.KotlinLogging
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [FeatureFlags] using LaunchDarkly's Java SDK.
 * See https://docs.launchdarkly.com/docs/java-sdk-reference documentation.
 */
@Singleton
class LaunchDarklyFeatureFlags @Inject constructor(
  private val ldClient: LDClientInterface,
  private val moshi: Moshi
) : AbstractIdleService(), FeatureFlags, FeatureService {
  override fun startUp() {
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
  }

  override fun shutDown() {
    ldClient.flush()
    ldClient.close()
  }

  override fun getBoolean(feature: Feature, key: String, attributes: Attributes): Boolean {
    val result = ldClient.boolVariationDetail(
      feature.name,
      buildUser(feature, key, attributes),
      false
    )
    checkDefaultNotUsed(feature, result)
    return result.value
  }

  override fun getInt(feature: Feature, key: String, attributes: Attributes): Int {
    checkInitialized()
    val result = ldClient.intVariationDetail(
      feature.name,
      buildUser(feature, key, attributes),
      0
    )

    checkDefaultNotUsed(feature, result)
    return result.value
  }

  override fun getString(feature: Feature, key: String, attributes: Attributes): String {
    checkInitialized()
    val result =
      ldClient.stringVariationDetail(
        feature.name,
        buildUser(feature, key, attributes),
        ""
      )
    checkDefaultNotUsed(feature, result)
    return result.value
  }

  override fun <T : Enum<T>> getEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    checkInitialized()
    val result =
      ldClient.stringVariationDetail(feature.name, buildUser(feature, key, attributes), "")
    checkDefaultNotUsed(feature, result)
    return java.lang.Enum.valueOf(clazz, result.value.toUpperCase())
  }

  override fun <T> getJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes
  ): T {
    checkInitialized()
    val result = ldClient.jsonValueVariationDetail(
      feature.name,
      buildUser(feature, key, attributes),
      LDValue.ofNull()
    )
    checkDefaultNotUsed(feature, result)
    return moshi.adapter(clazz).fromSafeJson(result.value.toJsonString())
      ?: throw IllegalArgumentException("null value deserialized from $feature")
  }

  private fun checkInitialized() {
    checkState(
      ldClient.isInitialized,
      "LaunchDarkly feature flags not initialized. " +
        "Did you forget to make your service depend on [FeatureFlags]?"
    )
  }

  private fun <T> checkDefaultNotUsed(feature: Feature, detail: EvaluationDetail<T>) {
    if (!detail.isDefaultValue) {
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

  companion object {
    val logger = KotlinLogging.logger {}
  }
}
