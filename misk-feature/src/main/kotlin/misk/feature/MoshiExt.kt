package misk.feature

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import mu.KotlinLogging

private val logger = KotlinLogging.logger(FeatureFlags::class.qualifiedName!!)

/**
 * Attempts to use [JsonAdapter.failOnUnknown] and logs any issues before falling back to ignoring
 * the unknown fields.
 */
fun <T> JsonAdapter<T>.toSafeJson(value: T): String {
  return try {
    failOnUnknown().toJson(value)
  } catch (e: JsonDataException) {
    logger.error(e) {
      "failed to serialize JSON due to unknown fields. ignoring those fields and trying again"
    }
    return toJson(value)
  }
}

/**
 * Attempts to use [JsonAdapter.failOnUnknown] and logs any issues before falling back to ignoring
 * the unknown fields.
 */
fun <T> JsonAdapter<T>.fromSafeJson(json: String): T? {
  return try {
    failOnUnknown().fromJson(json)
  } catch (e: JsonDataException) {
    logger.error(e) {
      "failed to parse JSON due to unknown fields. ignoring those fields and trying again"
    }
    return fromJson(json)
  }
}
