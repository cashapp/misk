package wisp.feature.testing

import com.squareup.moshi.Moshi
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import wisp.feature.*
import wisp.moshi.defaultKotlinMoshi

/** In-memory test implementation of [FeatureFlags] that allows flags to be overridden. */
class FakeLegacyFeatureFlags @Deprecated("Needed for Misk Provider usage...") constructor(val moshi: () -> Moshi) :
  LegacyFeatureFlags, DynamicConfig {

  /** Preferred constructor for Wisp */
  @Suppress("DEPRECATION") constructor(moshi: Moshi = defaultKotlinMoshi) : this({ moshi })

  companion object {
    const val KEY = "fake_dynamic_flag"
    val defaultAttributes = Attributes()
  }

  private val trackers = HashMap<MapKey, MutableList<TrackerMapValue<*>>>()
  private val overrides = ConcurrentHashMap<MapKey, PriorityQueue<MapValue>>()

  override fun getBoolean(feature: Feature, key: String, attributes: Attributes): Boolean =
    get(feature, key, attributes, Boolean::class.javaObjectType)

  override fun getDouble(feature: Feature, key: String, attributes: Attributes): Double =
    get(feature, key, attributes, Double::class.javaObjectType)

  override fun getInt(feature: Feature, key: String, attributes: Attributes): Int =
    get(feature, key, attributes, Int::class.javaObjectType)

  override fun getString(feature: Feature, key: String, attributes: Attributes): String =
    get(feature, key, attributes, String::class.javaObjectType)

  override fun <T : Enum<T>> getEnum(feature: Feature, key: String, clazz: Class<T>, attributes: Attributes): T =
    get(feature, key, attributes, clazz)

  override fun <T> getJson(feature: Feature, key: String, clazz: Class<T>, attributes: Attributes): T {
    val jsonFn = get(feature, key, attributes, JsonValue::class.java)
    // The JSON is lazily provided to handle the case where the override is provided by the
    // FakeFeatureFlagModule and the Moshi instance cannot be accessed inside the module.
    return moshi().adapter(clazz).fromSafeJson(jsonFn.get())
      ?: throw IllegalArgumentException("null value deserialized from $feature")
  }

  override fun getJsonString(feature: Feature, key: String, attributes: Attributes): String {
    val jsonFn = get(feature, key, attributes, JsonValue::class.java)
    return jsonFn.get()
  }

  override fun getBoolean(feature: Feature) = getBoolean(feature, KEY)

  override fun getDouble(feature: Feature) = getDouble(feature, KEY)

  override fun getInt(feature: Feature) = getInt(feature, KEY)

  override fun getString(feature: Feature) = getString(feature, KEY)

  override fun <T : Enum<T>> getEnum(feature: Feature, clazz: Class<T>): T = getEnum(feature, KEY, clazz)

  override fun <T> getJson(feature: Feature, clazz: Class<T>): T = getJson(feature, KEY, clazz)

  override fun getJsonString(feature: Feature): String = getJsonString(feature, KEY)

  private fun <T> get(feature: Feature, key: String, attributes: Attributes, clazz: Class<T>): T {
    val result = get(feature, key, attributes)
      ?: throw IllegalArgumentException("Flag $feature must be overridden with override() before use")
    try {
      return clazz.cast(result)
    } catch (_: ClassCastException) {
      throw IllegalArgumentException("Flag $feature: expecting $clazz but override() was called with ${result.javaClass}")
    }
  }

  private fun get(feature: Feature, key: String, attributes: Attributes): Any? {
    FeatureFlagValidation.checkValidKey(feature, key)
    // Override value lookup is structured in 3 levels: feature, key, and attributes level. The
    // logic to get the override value is as follows:
    //
    // 1. Return feature level override if there is no override at the key level.
    // 2. if there is an override at the key level, then:
    //   2.1 Return the override value associated with the attribute override that contains
    //       all the attributes of the given attributes.
    //   2.2 If there is no match, return the override value defined at the key level.
    val overrideMapValues = overrides[MapKey(feature, key)] ?: return overrides[MapKey(feature)]?.first()?.value

    val overrideMapValuesCopy = PriorityQueue(overrideMapValues)
    var currentMapValue = overrideMapValuesCopy.poll()
    while (overrideMapValuesCopy.isNotEmpty()) {
      if (
        attributes.text.entries.containsAll(currentMapValue.attributes.text.entries) &&
          (attributes.number?.entries ?: emptySet()).containsAll(
            currentMapValue.attributes.number?.entries ?: emptySet()
          )
      ) {
        break
      }
      currentMapValue = overrideMapValuesCopy.poll()
    }
    return currentMapValue.value
  }

  private fun <T> trackAny(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit,
  ): TrackerReference =
    synchronized(trackers) {
      val bucket = trackers.computeIfAbsent(MapKey(feature, key)) { mutableListOf() }
      val value = TrackerMapValue(attributes, executor, tracker)
      bucket.add(value)
      return object : TrackerReference {
        override fun unregister() {
          bucket.remove(value)
        }
      }
    }

  override fun trackBoolean(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Boolean) -> Unit,
  ) = trackAny(feature, key, attributes, executor, tracker)

  override fun trackDouble(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Double) -> Unit,
  ) = trackAny(feature, key, attributes, executor, tracker)

  override fun trackInt(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (Int) -> Unit,
  ) = trackAny(feature, key, attributes, executor, tracker)

  override fun trackString(
    feature: Feature,
    key: String,
    attributes: Attributes,
    executor: Executor,
    tracker: (String) -> Unit,
  ) = trackAny(feature, key, attributes, executor, tracker)

  override fun <T : Enum<T>> trackEnum(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit,
  ) = trackAny(feature, key, attributes, executor, tracker)

  override fun <T> trackJson(
    feature: Feature,
    key: String,
    clazz: Class<T>,
    attributes: Attributes,
    executor: Executor,
    tracker: (T) -> Unit,
  ) = trackAny(feature, key, attributes, executor, tracker)

  override fun trackBoolean(feature: Feature, executor: Executor, tracker: (Boolean) -> Unit) =
    trackBoolean(feature, KEY, executor, tracker)

  override fun trackDouble(feature: Feature, executor: Executor, tracker: (Double) -> Unit) =
    trackDouble(feature, KEY, executor, tracker)

  override fun trackInt(feature: Feature, executor: Executor, tracker: (Int) -> Unit) =
    trackInt(feature, KEY, executor, tracker)

  override fun trackString(feature: Feature, executor: Executor, tracker: (String) -> Unit) =
    trackString(feature, KEY, executor, tracker)

  override fun <T : Enum<T>> trackEnum(feature: Feature, clazz: Class<T>, executor: Executor, tracker: (T) -> Unit) =
    trackEnum(feature, KEY, clazz, executor, tracker)

  override fun <T> trackJson(feature: Feature, clazz: Class<T>, executor: Executor, tracker: (T) -> Unit) =
    trackJson(feature, KEY, clazz, executor, tracker)

  fun override(feature: Feature, value: Boolean) = override<Boolean>(feature, value)

  fun override(feature: Feature, value: Double) = override<Double>(feature, value)

  fun override(feature: Feature, value: Int) = override<Int>(feature, value)

  fun override(feature: Feature, value: String) = override<String>(feature, value)

  fun override(feature: Feature, value: Enum<*>) = override<Enum<*>>(feature, value)

  fun <T> override(feature: Feature, value: T) = overrideKey(feature, KEY, value, defaultAttributes)

  fun <T> override(feature: Feature, value: T, clazz: Class<T>) {
    val jsonValue = JsonValue { moshi().adapter(clazz).toSafeJson(value) }
    overrideKey(feature, KEY, jsonValue, defaultAttributes)
  }

  fun overrideJsonString(feature: Feature, json: String) {
    overrideKey(feature, KEY, JsonValue { json }, defaultAttributes)
  }

  inline fun <reified T> overrideJson(feature: Feature, value: T) {
    overrideKeyJson(feature, KEY, value, defaultAttributes)
  }

  @JvmOverloads
  fun overrideKey(feature: Feature, key: String, value: Boolean, attributes: Attributes = defaultAttributes) =
    overrideKey<Boolean>(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(feature: Feature, key: String, value: Double, attributes: Attributes = defaultAttributes) =
    overrideKey<Double>(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(feature: Feature, key: String, value: Int, attributes: Attributes = defaultAttributes) =
    overrideKey<Int>(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(feature: Feature, key: String, value: String, attributes: Attributes = defaultAttributes) =
    overrideKey<String>(feature, key, value, attributes)

  @JvmOverloads
  fun overrideKey(feature: Feature, key: String, value: Enum<*>, attributes: Attributes = defaultAttributes) =
    overrideKey<Enum<*>>(feature, key, value, attributes)

  fun <T> overrideKey(feature: Feature, key: String, value: T, clazz: Class<T>) {
    val jsonValue = JsonValue { moshi().adapter(clazz).toSafeJson(value) }
    overrideKey(feature, key, jsonValue, defaultAttributes)
  }

  @JvmOverloads
  fun <T> overrideKey(feature: Feature, key: String, value: T, attributes: Attributes = defaultAttributes) {
    overrideKey<T, T>(feature, key, value, attributes, mapper = null)
  }

  @JvmOverloads
  fun <T, V> overrideKey(
    feature: Feature,
    key: String,
    value: T,
    attributes: Attributes = defaultAttributes,
    mapper: ((T) -> V)?,
  ) {
    val mapKey = MapKey(feature, key)
    overrides
      .computeIfAbsent(mapKey) { PriorityQueue() }
      .add(MapValue(order = overrides[mapKey]!!.size + 1, attributes = attributes, value = value as Any))

    synchronized(trackers) {
      trackers[mapKey]
        ?.filter { r ->
          r.attributes.text.entries.containsAll(attributes.text.entries) &&
            (r.attributes.number?.entries ?: emptySet()).containsAll(attributes.number?.entries ?: emptySet())
        }
        ?.forEach { r ->
          @Suppress("UNCHECKED_CAST")
          r.executor.execute { (r as TrackerMapValue<V>).tracker(mapper?.let { it(value) } ?: (value as V)) }
        }
    }
  }

  @JvmOverloads
  inline fun <reified T> overrideKeyJson(
    feature: Feature,
    key: String,
    value: T,
    attributes: Attributes = defaultAttributes,
  ) {
    val adapter = moshi().adapter(T::class.java)
    val jsonValue = JsonValue { adapter.toSafeJson(value) }
    overrideKey<JsonValue, T>(feature, key, jsonValue, attributes, { adapter.fromSafeJson(it.get())!! })
  }

  @JvmOverloads
  fun overrideKeyJsonString(feature: Feature, key: String, value: String, attributes: Attributes = defaultAttributes) {
    overrideKey(feature, key, JsonValue { value }, attributes)
  }

  fun reset() {
    overrides.clear()
  }

  private data class MapKey(val feature: Feature, val key: String = KEY)

  /** Data class that holds the override value provided for the given [feature, key, attributes]. */
  private data class MapValue(
    val order: Int = 1, // Order is used to pick the latest provided map value.
    val attributes: Attributes = defaultAttributes,
    val value: Any,
  ) : Comparable<MapValue> {
    override fun compareTo(other: MapValue): Int {
      val textAttributeSizeCompare = -attributes.text.size.compareTo(other.attributes.text.size)
      return if (textAttributeSizeCompare != 0) {
        textAttributeSizeCompare
      } else {
        val numberAttributeSizeCompare = -(attributes.number?.size ?: 0).compareTo(other.attributes.number?.size ?: 0)
        if (numberAttributeSizeCompare != 0) {
          numberAttributeSizeCompare
        } else {
          -order.compareTo(other.order)
        }
      }
    }
  }

  /** Data class that holds a tracker for the given [feature, key, attributes]. */
  private data class TrackerMapValue<T>(
    val attributes: Attributes = defaultAttributes,
    val executor: Executor,
    val tracker: (T) -> Unit,
  )

  /** Provides a JSON value lazily. */
  fun interface JsonValue {
    fun get(): String
  }
}
