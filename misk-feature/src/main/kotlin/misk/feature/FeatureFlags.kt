package misk.feature

import wisp.feature.Attributes
import wisp.feature.Feature
import wisp.feature.TrackerReference
import java.util.concurrent.Executor

/**
 * Interface for evaluating feature flags.
 */
interface FeatureFlags : wisp.feature.FeatureFlags

inline fun <reified T : Enum<T>> wisp.feature.FeatureFlags.getEnum(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes()
): T = getEnum(feature, key, T::class.java, attributes)

inline fun <reified T> wisp.feature.FeatureFlags.getJson(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes()
): T = getJson(feature, key, T::class.java, attributes)

inline fun <reified T : Enum<T>> wisp.feature.FeatureFlags.trackEnum(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes(),
  executor: Executor,
  noinline tracker: (T) -> Unit
): TrackerReference = trackEnum(feature, key, T::class.java, attributes, executor, tracker)

inline fun <reified T> wisp.feature.FeatureFlags.trackJson(
  feature: Feature,
  key: String,
  attributes: Attributes = Attributes(),
  executor: Executor,
  noinline tracker: (T) -> Unit
): TrackerReference = trackJson(feature, key, T::class.java, attributes, executor, tracker)
