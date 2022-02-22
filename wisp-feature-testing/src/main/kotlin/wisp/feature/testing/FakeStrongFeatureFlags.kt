package wisp.feature.testing

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
import wisp.feature.StringFeatureFlag
import wisp.feature.StrongFeatureFlags
import wisp.feature.TrackerReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlin.reflect.KClass

/**
 * In-memory test implementation of [FeatureFlags] that allows for overriding strong feature flags
 */
class FakeStrongFeatureFlags : StrongFeatureFlags {
  private val featureConfigs = ConcurrentHashMap<Class<out FeatureFlag<*>>, FeatureFlagConfig<*>>()

  private fun <T : Any> getFeatureConfig(clazz: Class<out FeatureFlag<T>>): FeatureFlagConfig<T> {
    val featureConfig = featureConfigs.getOrPut(clazz) { FeatureFlagConfig<T>() }

    @Suppress("UNCHECKED_CAST")
    return featureConfig as FeatureFlagConfig<T>
  }

  private data class FeatureFlagConfig<T : Any>(
    val matchers: MutableList<FeatureMatcher<T>> = mutableListOf(),
    val trackers: MutableList<FeatureTracker<T>> = mutableListOf()
  )

  private data class FeatureMatcher<T : Any>(
    val condition: (FeatureFlag<in T>) -> Boolean,
    val value: T
  )

  private data class FeatureTracker<T : Any>(
    val flag: FeatureFlag<T>,
    val executor: Executor,
    val tracker: (T) -> Unit
  )

  fun reset(): FakeStrongFeatureFlags {
    featureConfigs.clear()
    return this
  }

  /**
   * Generic flag override function.
   *
   * Prefer [override] instead for a more convenient interface.
   */
  fun <T : Any, Flag : FeatureFlag<in T>> overrideAny(
    clazz: Class<out FeatureFlag<T>>,
    value: T,
    matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeStrongFeatureFlags {
    val featureConfig = getFeatureConfig(clazz)

    @Suppress("UNCHECKED_CAST")
    val typedMatcher = matcher as (FeatureFlag<in T>) -> Boolean
    val featureMatcher = FeatureMatcher(typedMatcher, value)
    featureConfig.matchers.add(featureMatcher)

    synchronized(featureConfig.trackers) {
      val matchingTrackers = featureConfig.trackers.filter { featureMatcher.condition(it.flag) }
      matchingTrackers.forEach { tracker ->
        tracker.executor.execute { tracker.tracker(value) }
      }
    }

    return this
  }

  inline fun <reified Flag : BooleanFeatureFlag> override(
    value: Boolean,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeStrongFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag : StringFeatureFlag> override(
    value: String,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeStrongFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag: IntFeatureFlag> override(
    value: Int,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeStrongFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag: DoubleFeatureFlag> override(
    value: Double,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeStrongFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag: JsonFeatureFlag<T>, T : Any> override(
    value: T,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeStrongFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  inline fun <reified Flag: EnumFeatureFlag<T>, T : Enum<T>> override(
    value: T,
    noinline matcher: (Flag) -> Boolean = { _ -> true }
  ): FakeStrongFeatureFlags = overrideAny(Flag::class.java, value, matcher)

  private fun <T : Any> getAny(flag: FeatureFlag<T>): T {
    val featureConfig = getFeatureConfig(flag.javaClass)
    return featureConfig.matchers.lastOrNull() { it.condition(flag) }?.value
      ?: error("no flag match found for $flag")
  }

  override fun get(flag: BooleanFeatureFlag): Boolean = getAny(flag)
  override fun get(flag: StringFeatureFlag): String = getAny(flag)
  override fun get(flag: IntFeatureFlag): Int = getAny(flag)
  override fun get(flag: DoubleFeatureFlag): Double = getAny(flag)
  override fun <T : Enum<T>> get(flag: EnumFeatureFlag<T>): T = getAny(flag)
  override fun <T : Any> get(flag: JsonFeatureFlag<T>): T = getAny(flag)

  override fun track(
    flag: BooleanFeatureFlag,
    executor: Executor,
    tracker: (Boolean) -> Unit
  ): TrackerReference = trackAny(flag, flag.javaClass, executor, tracker)

  override fun track(
    flag: StringFeatureFlag,
    executor: Executor,
    tracker: (String) -> Unit
  ): TrackerReference = trackAny(flag, flag.javaClass, executor, tracker)

  override fun track(
    flag: IntFeatureFlag,
    executor: Executor,
    tracker: (Int) -> Unit
  ): TrackerReference = trackAny(flag, flag.javaClass, executor, tracker)

  override fun track(
    flag: DoubleFeatureFlag,
    executor: Executor,
    tracker: (Double) -> Unit
  ): TrackerReference = trackAny(flag, flag.javaClass, executor, tracker)

  override fun <T : Enum<T>> track(
    flag: EnumFeatureFlag<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ): TrackerReference = trackAny(flag, flag.javaClass, executor, tracker)

  override fun <T : Any> track(
    flag: JsonFeatureFlag<T>,
    executor: Executor,
    tracker: (T) -> Unit
  ): TrackerReference = trackAny(flag, flag.javaClass, executor, tracker)

  private fun <T : Any> trackAny(
    flag: FeatureFlag<T>,
    clazz: Class<out FeatureFlag<T>>,
    executor: Executor,
    tracker: (T) -> Unit
  ): TrackerReference {
    val featureConfig = getFeatureConfig(clazz)

    return synchronized(featureConfig.trackers) {
      val featureTracker = FeatureTracker(flag, executor, tracker)
      featureConfig.trackers.add(featureTracker)

      object : TrackerReference {
        override fun unregister() {
          featureConfig.trackers.remove(featureTracker)
        }
      }
    }
  }
}

