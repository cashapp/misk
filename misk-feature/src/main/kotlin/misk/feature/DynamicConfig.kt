package misk.feature

import wisp.feature.Feature
import wisp.feature.TrackerReference
import java.util.concurrent.Executor

/**
 * Interface for evaluating dynamic flags. Dynamic flags are similar to feature flags, but they
 * don't support different variations for different keys.
 */
interface DynamicConfig : wisp.feature.DynamicConfig

inline fun <reified T : Enum<T>> wisp.feature.DynamicConfig.getEnum(
  feature: Feature
): T = getEnum(feature, T::class.java)

inline fun <reified T : Enum<T>> wisp.feature.DynamicConfig.trackEnum(
  feature: Feature,
  executor: Executor,
  noinline tracker: (T) -> Unit
): TrackerReference = trackEnum(feature, T::class.java, executor, tracker)

inline fun <reified T> wisp.feature.DynamicConfig.getJson(feature: Feature): T = getJson(
  feature,
  T::class.java
)

inline fun <reified T> wisp.feature.DynamicConfig.trackJson(
  feature: Feature,
  executor: Executor,
  noinline tracker: (T) -> Unit
): TrackerReference = trackJson(feature, T::class.java, executor, tracker)
