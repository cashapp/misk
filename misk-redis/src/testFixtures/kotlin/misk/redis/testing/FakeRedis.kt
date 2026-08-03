package misk.redis.testing

import jakarta.inject.Inject
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.SortedMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import misk.redis.DeferredRedis
import misk.redis.Redis
import misk.redis.Redis.ZAddOptions.CH
import misk.redis.Redis.ZAddOptions.GT
import misk.redis.Redis.ZAddOptions.LT
import misk.redis.Redis.ZAddOptions.NX
import misk.redis.Redis.ZAddOptions.XX
import misk.redis.Redis.ZRangeIndexMarker
import misk.redis.Redis.ZRangeLimit
import misk.redis.Redis.ZRangeMarker
import misk.redis.Redis.ZRangeRankMarker
import misk.redis.Redis.ZRangeScoreMarker
import misk.redis.Redis.ZRangeType
import misk.redis.checkHrandFieldCount
import misk.testing.TestFixture
import okio.ByteString
import okio.ByteString.Companion.encode
import okio.ByteString.Companion.encodeUtf8
import org.apache.commons.io.FilenameUtils
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.args.ListDirection

/**
 * An in-memory key-value store which closely mimics [misk.redis.RealRedis].
 *
 * This should be used if:
 * - It is undesirable to start an actual Redis instance via [DockerRedis] in test.
 * - You need fine-grained control over randomness in tests
 * - You need fine-grained control over key-expiry in tests
 *
 * Caveats:
 * - FakeRedis does not currently support [Redis.multi] transactions
 */
class FakeRedis @Inject constructor(private val clock: Clock, @ForFakeRedis private val random: Random) :
  Redis, TestFixture {
  /**
   * Represents a value in the key-value store. By grouping all data types that the [Redis] interface supports under a
   * single sealed class we can use a single key-value store for all entries which simplifies implementation of several
   * Redis operations and better mimic in tests how Redis actually behaves.
   */
  private sealed interface Value {
    var expiryInstant: Instant

    data class String(val data: ByteString, override var expiryInstant: Instant) : Value

    /** A nested hash map for hash operations. */
    data class Hash(val data: ConcurrentHashMap<kotlin.String, ByteString>, override var expiryInstant: Instant) : Value

    /** A hash map for list operations. */
    data class List(val data: kotlin.collections.List<ByteString>, override var expiryInstant: Instant) : Value

    /**
     * Note: Redis sorted set actually orders by value. It is quite complex to implement it here. In this Fake Redis
     * implementation which is generally used for testing, we have simply used a HashMap to key score->members. So any
     * sorting based on values will have to be handled in the implementation of the functions for this sorted set.
     */
    data class SortedSet(val data: SortedMap<Double, HashSet<kotlin.String>>, override var expiryInstant: Instant) :
      Value
  }

  private inner class KeyValueStore(private val store: ConcurrentHashMap<String, Value>) :
    ConcurrentMap<String, Value> by store {
    override operator fun get(key: String): Value? {
      val value = store[key] ?: return null
      if (clock.instant() >= value.expiryInstant) {
        store.remove(key)
        return null
      }
      return value
    }

    override fun containsKey(key: String): Boolean = get(key) != null

    inline fun <reified T : Value> getTyped(key: String): T? {
      val value = this[key] ?: return null

      return when (value) {
        is T -> value
        else -> throw RuntimeException("WRONGTYPE Operation against a key holding the wrong kind of value")
      }
    }
  }

  /** Acts as the Redis key-value store. */
  private val keyValueStore = KeyValueStore(ConcurrentHashMap<String, Value>())

  override fun reset() {
    keyValueStore.clear()
  }

  @Synchronized
  override fun del(key: String): Boolean {
    return keyValueStore.remove(key) != null
  }

  @Synchronized override fun del(vararg keys: String): Int = keys.count { del(it) }

  @Synchronized
  override fun mget(vararg keys: String): List<ByteString?> {
    // MGET returns null instead of throwing for non-string values so we can't reuse the regular GET function
    return keys.map { key ->
      when (val value = keyValueStore[key]) {
        is Value.String -> value.data
        else -> null
      }
    }
  }

  @Synchronized
  override fun mset(vararg keyValues: ByteString) {
    require(keyValues.size % 2 == 0) { "Wrong number of arguments to mset" }

    (keyValues.indices step 2).forEach { set(keyValues[it].utf8(), keyValues[it + 1]) }
  }

  @Synchronized
  override fun get(key: String): ByteString? {
    val value = keyValueStore.getTyped<Value.String>(key) ?: return null

    return value.data
  }

  @Synchronized
  override fun getDel(key: String): ByteString? {
    val value = get(key)
    keyValueStore.remove(key)
    return value
  }

  @Synchronized
  override fun hdel(key: String, vararg fields: String): Long {
    val value = keyValueStore.getTyped<Value.Hash>(key) ?: return 0L

    var countDeleted = 0L
    fields.forEach {
      if (value.data.containsKey(it)) {
        value.data.remove(it)
        countDeleted++
      }
    }
    return countDeleted
  }

  @Synchronized
  override fun hget(key: String, field: String): ByteString? {
    val value = keyValueStore.getTyped<Value.Hash>(key) ?: return null

    return value.data[field]
  }

  @Synchronized
  override fun hgetAll(key: String): Map<String, ByteString> {
    val value = keyValueStore.getTyped<Value.Hash>(key) ?: return emptyMap()

    return value.data.mapValues { it.value }
  }

  @Synchronized
  override fun hlen(key: String): Long {
    val value = keyValueStore.getTyped<Value.Hash>(key) ?: return 0L

    return value.data.size.toLong()
  }

  @Synchronized
  override fun hkeys(key: String): List<ByteString> {
    val value = keyValueStore.getTyped<Value.Hash>(key) ?: return emptyList()

    return value.data.keys().toList().map { it.encode(Charsets.UTF_8) }
  }

  @Synchronized
  override fun hmget(key: String, vararg fields: String): List<ByteString?> {
    val hash: Map<String, ByteString> = keyValueStore.getTyped<Value.Hash>(key)?.data ?: emptyMap()
    return buildList {
      for (field in fields) {
        add(hash[field])
      }
    }
  }

  @Synchronized
  override fun hincrBy(key: String, field: String, increment: Long): Long {
    val encodedValue = hget(key, field)?.utf8() ?: "0"
    val value = encodedValue.toLong() + increment
    hset(key, field, value.toString().encode(Charsets.UTF_8))
    return value
  }

  @Synchronized
  override fun hrandFieldWithValues(key: String, count: Long): Map<String, ByteString>? =
    randomFields(key, count)?.toMap()

  @Synchronized
  override fun hrandField(key: String, count: Long): List<String> =
    randomFields(key, count)?.map { it.first } ?: emptyList()

  private fun randomFields(key: String, count: Long): List<Pair<String, ByteString>>? {
    checkHrandFieldCount(count)
    return hgetAll(key)?.toList()?.shuffled(random)?.take(count.toInt())
  }

  // Cursor and count are ignored for fake implementation. All matches are always
  // returned without pagination.
  @Synchronized
  override fun scan(cursor: String, matchPattern: String?, count: Int?): Redis.ScanResult {
    val matchingKeys = mutableListOf<String>()
    keyValueStore.keys.forEach { key ->
      if (matchPattern == null || FilenameUtils.wildcardMatch(key, matchPattern)) {
        matchingKeys.add(key)
      }
    }
    return Redis.ScanResult("0", matchingKeys)
  }

  @Synchronized
  override fun set(key: String, value: ByteString) {
    // Set the key to expire at the latest possible instant
    keyValueStore[key] = Value.String(data = value, expiryInstant = Instant.MAX)
  }

  @Synchronized
  override fun set(key: String, expiryDuration: Duration, value: ByteString) {
    keyValueStore[key] = Value.String(data = value, expiryInstant = clock.instant().plusSeconds(expiryDuration.seconds))
  }

  @Synchronized
  override fun setnx(key: String, value: ByteString): Boolean {
    return setWithExpiry(key, value, expiryInstant = Instant.MAX)
  }

  @Synchronized
  override fun setnx(key: String, expiryDuration: Duration, value: ByteString): Boolean {
    return setWithExpiry(key, value, clock.instant().plusSeconds(expiryDuration.seconds))
  }

  private fun setWithExpiry(key: String, value: ByteString, expiryInstant: Instant): Boolean {
    return keyValueStore.putIfAbsent(key, Value.String(data = value, expiryInstant)) == null
  }

  @Synchronized
  override fun hset(key: String, field: String, value: ByteString): Long {
    if (!keyValueStore.containsKey(key)) {
      keyValueStore[key] = Value.Hash(data = ConcurrentHashMap(), expiryInstant = Instant.MAX)
    }
    val valueHash = keyValueStore.getTyped<Value.Hash>(key)!!
    val newFieldCount = if (valueHash.data[field] != null) 0L else 1L
    valueHash.data[field] = value
    return newFieldCount
  }

  @Synchronized
  override fun hset(key: String, hash: Map<String, ByteString>): Long {
    return hash.entries.sumOf { (field, value) -> hset(key, field, value) }
  }

  @Synchronized override fun incr(key: String): Long = incrBy(key, 1)

  @Synchronized
  override fun incrBy(key: String, increment: Long): Long {
    val encodedValue = get(key)?.utf8() ?: "0"
    val value = encodedValue.toLong() + increment
    set(key, value.toString().encode(Charsets.UTF_8))
    return value
  }

  @Synchronized
  override fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double,
  ): ByteString? = lmove(sourceKey, destinationKey, from, to)

  @Synchronized
  override fun brpoplpush(sourceKey: String, destinationKey: String, timeoutSeconds: Int) =
    blmove(
      sourceKey = sourceKey,
      destinationKey = destinationKey,
      from = ListDirection.RIGHT,
      to = ListDirection.LEFT,
      timeoutSeconds = timeoutSeconds.toDouble(),
    )

  @Synchronized
  override fun lmove(sourceKey: String, destinationKey: String, from: ListDirection, to: ListDirection): ByteString? {
    val sourceExisting = keyValueStore.getTyped<Value.List>(sourceKey) ?: return null
    val sourceExpiry = sourceExisting.expiryInstant
    val sourceList = sourceExisting.data.toMutableList()
    val sourceValue =
      when (from) {
        ListDirection.LEFT -> sourceList.removeFirst()
        ListDirection.RIGHT -> sourceList.removeLast()
      }
    keyValueStore[sourceKey] = Value.List(data = sourceList, expiryInstant = sourceExpiry)

    val destinationExisting = keyValueStore.getTyped<Value.List>(destinationKey)
    val destinationExpiry = destinationExisting?.expiryInstant ?: Instant.MAX
    val destinationList = destinationExisting?.data?.toMutableList() ?: mutableListOf()
    when (to) {
      ListDirection.LEFT -> destinationList.add(index = 0, element = sourceValue)
      ListDirection.RIGHT -> destinationList.add(element = sourceValue)
    }
    keyValueStore[destinationKey] = Value.List(data = destinationList, expiryInstant = destinationExpiry)
    return sourceValue
  }

  @Synchronized
  override fun lpush(key: String, vararg elements: ByteString): Long {
    val existing = keyValueStore.getTyped<Value.List>(key)
    val currentExpiry = existing?.expiryInstant ?: Instant.MAX
    val updated = existing?.data?.toMutableList() ?: mutableListOf()
    for (element in elements) {
      updated.add(0, element)
    }
    keyValueStore[key] = Value.List(data = updated, expiryInstant = currentExpiry)
    return updated.size.toLong()
  }

  @Synchronized
  override fun rpush(key: String, vararg elements: ByteString): Long {
    val existing = keyValueStore.getTyped<Value.List>(key)
    val currentExpiry = existing?.expiryInstant ?: Instant.MAX
    val updated = existing?.data?.toMutableList() ?: mutableListOf()
    updated.addAll(elements)
    keyValueStore[key] = Value.List(data = updated, expiryInstant = currentExpiry)
    return updated.size.toLong()
  }

  @Synchronized
  override fun lpop(key: String, count: Int): List<ByteString?> {
    val value = keyValueStore.getTyped<Value.List>(key) ?: return emptyList()
    val result = with(value) { data.subList(0, min(data.size, count)).toList() }
    keyValueStore[key] = value.copy(data = value.data.drop(count))
    return result
  }

  @Synchronized override fun lpop(key: String): ByteString? = lpop(key, count = 1).firstOrNull()

  @Synchronized
  override fun blpop(keys: Array<String>, timeoutSeconds: Double): Pair<String, ByteString>? {
    // For the fake implementation, we'll check each key in order and return the first non-empty list
    for (key in keys) {
      val element = lpop(key)
      if (element != null) {
        return Pair(key, element)
      }
    }
    // In a real implementation, this would block until timeout or an element is available
    // For the fake, we just return null immediately
    return null
  }

  @Synchronized
  override fun rpop(key: String, count: Int): List<ByteString?> {
    val value = keyValueStore.getTyped<Value.List>(key) ?: return emptyList()
    val result = with(value) { data.takeLast(min(data.size, count)).asReversed() }
    keyValueStore[key] = value.copy(data = value.data.dropLast(count))
    return result
  }

  @Synchronized
  override fun llen(key: String): Long {
    return keyValueStore.getTyped<Value.List>(key)?.data?.size?.toLong() ?: 0L
  }

  override fun rpop(key: String): ByteString? = rpop(key, count = 1).firstOrNull()

  @Synchronized
  override fun lrange(key: String, start: Long, stop: Long): List<ByteString?> {
    val list = keyValueStore.getTyped<Value.List>(key)?.data ?: return emptyList()
    if (start >= list.size) return emptyList()

    // Redis allows negative values starting from the end of the list.
    val first = if (start < 0) list.size + start else start
    val last = if (stop < 0) list.size + stop else stop

    // Redis is inclusive on both sides; Kotlin only on start.
    return list.subList(max(0, first.toInt()), min(last.toInt() + 1, list.size))
  }

  @Synchronized
  override fun ltrim(key: String, start: Long, stop: Long) {
    val value = keyValueStore.getTyped<Value.List>(key) ?: return
    val expiry = value.expiryInstant
    val list = value.data

    val startIdx = if (start < 0) list.size + start else start
    val stopIdx = if (stop < 0) list.size + stop else stop
    if (startIdx > stopIdx || startIdx >= list.size) {
      keyValueStore[key] = Value.List(data = emptyList(), expiryInstant = expiry)
      return
    }
    val trimmedList = list.subList(max(0, startIdx.toInt()), min(list.size, stopIdx.toInt() + 1))
    keyValueStore[key] = Value.List(data = trimmedList, expiryInstant = expiry)
  }

  override fun lrem(key: String, count: Long, element: ByteString): Long {
    val value = keyValueStore.getTyped<Value.List>(key) ?: return 0L
    val expiry = value.expiryInstant

    val list = value.data.toMutableList()
    var totalCount = count
    val iterList =
      if (count < 0) {
        totalCount = -totalCount
        list.asReversed()
      } else {
        list
      }

    var deleteCount = 0L
    while ((count == 0L || deleteCount < totalCount) && iterList.contains(element)) {
      iterList.remove(element)
      deleteCount += 1
    }
    keyValueStore[key] = Value.List(data = list, expiryInstant = expiry)

    return deleteCount
  }

  @Synchronized
  override fun rpoplpush(sourceKey: String, destinationKey: String) =
    lmove(sourceKey = sourceKey, destinationKey = destinationKey, from = ListDirection.RIGHT, to = ListDirection.LEFT)

  override fun exists(key: String): Boolean {
    return keyValueStore.containsKey(key)
  }

  override fun exists(vararg key: String): Long {
    return key.sumOf { if (exists(it)) 1L else 0L }
  }

  override fun persist(key: String): Boolean {
    val value = keyValueStore[key]

    if (value != null) {
      value.expiryInstant = Instant.MAX
      return true
    } else {
      return false
    }
  }

  @Synchronized
  override fun expire(key: String, seconds: Long): Boolean {
    val ttlMillis = Duration.ofSeconds(seconds).toMillis()
    return pExpireAt(key, clock.millis().plus(ttlMillis))
  }

  @Synchronized
  override fun expireAt(key: String, timestampSeconds: Long): Boolean {
    val epochMillis = Instant.ofEpochSecond(timestampSeconds).toEpochMilli()
    return pExpireAt(key, epochMillis)
  }

  @Synchronized
  override fun pExpire(key: String, milliseconds: Long): Boolean = pExpireAt(key, clock.millis().plus(milliseconds))

  @Synchronized
  override fun pExpireAt(key: String, timestampMilliseconds: Long): Boolean {
    val value = keyValueStore[key]

    if (value != null) {
      value.expiryInstant = Instant.ofEpochMilli(timestampMilliseconds)
      return true
    } else {
      return false
    }
  }

  override fun watch(vararg keys: String) {
    // no op
  }

  override fun unwatch(vararg keys: String) {
    // no op
  }

  override fun multi(): Transaction {
    throw NotImplementedError("Fake client not implemented for this operation")
  }

  @Deprecated("Use pipelining instead.")
  override fun pipelined(): Pipeline {
    throw NotImplementedError("Use pipelining instead.")
  }

  override fun pipelining(block: DeferredRedis.() -> Unit) {
    FakePipelinedRedis().block()
  }

  /**
   * A poor implementation of pipelining for testing purposes. Unlike a real pipeline, this does not queue commands:
   * each one runs as it is called rather than when the block closes. What a caller observes after the block are a real
   * pipeline's semantics — every command ran exactly once, in order.
   */
  inner class FakePipelinedRedis : DeferredRedis {
    /**
     * Runs [block] now and replays its outcome to every read of the returned supplier. A real pipeline sends each
     * queued command exactly once when the block closes, so by the time anyone reads a reply it is already fixed: a
     * supplier that re-ran the command would execute it again on every read, and one never read at all would skip it
     * entirely. A failure is held rather than thrown here, because a real pipeline reports it from the reply too.
     */
    private fun <T> eager(block: () -> T): Supplier<T> {
      val outcome = runCatching(block)
      return Supplier { outcome.getOrThrow() }
    }

    override fun del(key: String): Supplier<Boolean> = eager { this@FakeRedis.del(key) }

    override fun del(vararg keys: String): Supplier<Int> = eager { this@FakeRedis.del(*keys) }

    override fun mget(vararg keys: String): Supplier<List<ByteString?>> = eager { this@FakeRedis.mget(*keys) }

    override fun mset(vararg keyValues: ByteString): Supplier<Unit> = eager { this@FakeRedis.mset(*keyValues) }

    override fun get(key: String): Supplier<ByteString?> = eager { this@FakeRedis[key] }

    override fun getDel(key: String): Supplier<ByteString?> = eager { this@FakeRedis.getDel(key) }

    override fun hdel(key: String, vararg fields: String): Supplier<Long> = eager { this@FakeRedis.hdel(key, *fields) }

    override fun hget(key: String, field: String): Supplier<ByteString?> = eager { this@FakeRedis.hget(key, field) }

    override fun hgetAll(key: String): Supplier<Map<String, ByteString>?> = eager { this@FakeRedis.hgetAll(key) }

    override fun hlen(key: String): Supplier<Long> = eager { this@FakeRedis.hlen(key) }

    override fun hkeys(key: String): Supplier<List<ByteString>> = eager { this@FakeRedis.hkeys(key) }

    override fun hmget(key: String, vararg fields: String): Supplier<List<ByteString?>> = eager {
      this@FakeRedis.hmget(key, *fields)
    }

    override fun hincrBy(key: String, field: String, increment: Long): Supplier<Long> = eager {
      this@FakeRedis.hincrBy(key, field, increment)
    }

    override fun hrandFieldWithValues(key: String, count: Long): Supplier<Map<String, ByteString>?> = eager {
      this@FakeRedis.hrandFieldWithValues(key, count)
    }

    override fun hrandField(key: String, count: Long): Supplier<List<String>> = eager {
      this@FakeRedis.hrandField(key, count)
    }

    override fun set(key: String, value: ByteString, expiryDuration: Duration?): Supplier<Unit> = eager {
      if (expiryDuration == null) {
        this@FakeRedis[key] = value
      } else {
        this@FakeRedis[key, expiryDuration] = value
      }
    }

    override fun setnx(key: String, value: ByteString, expiryDuration: Duration?): Supplier<Boolean> = eager {
      if (expiryDuration == null) {
        this@FakeRedis.setnx(key, value)
      } else {
        this@FakeRedis.setnx(key, expiryDuration, value)
      }
    }

    override fun hset(key: String, field: String, value: ByteString): Supplier<Long> = eager {
      this@FakeRedis.hset(key, field, value)
    }

    override fun hset(key: String, hash: Map<String, ByteString>): Supplier<Long> = eager {
      this@FakeRedis.hset(key, hash)
    }

    override fun incr(key: String): Supplier<Long> = eager { this@FakeRedis.incr(key) }

    override fun incrBy(key: String, increment: Long): Supplier<Long> = eager { this@FakeRedis.incrBy(key, increment) }

    override fun blmove(
      sourceKey: String,
      destinationKey: String,
      from: ListDirection,
      to: ListDirection,
      timeoutSeconds: Double,
    ): Supplier<ByteString?> = eager { this@FakeRedis.blmove(sourceKey, destinationKey, from, to, timeoutSeconds) }

    override fun brpoplpush(sourceKey: String, destinationKey: String, timeoutSeconds: Int): Supplier<ByteString?> =
      eager {
        this@FakeRedis.brpoplpush(sourceKey, destinationKey, timeoutSeconds)
      }

    override fun lmove(
      sourceKey: String,
      destinationKey: String,
      from: ListDirection,
      to: ListDirection,
    ): Supplier<ByteString?> = eager { this@FakeRedis.lmove(sourceKey, destinationKey, from, to) }

    override fun lpush(key: String, vararg elements: ByteString): Supplier<Long> = eager {
      this@FakeRedis.lpush(key, *elements)
    }

    override fun rpush(key: String, vararg elements: ByteString): Supplier<Long> = eager {
      this@FakeRedis.rpush(key, *elements)
    }

    override fun lpop(key: String, count: Int): Supplier<List<ByteString?>> = eager { this@FakeRedis.lpop(key, count) }

    override fun lpop(key: String): Supplier<ByteString?> = eager { this@FakeRedis.lpop(key) }

    override fun blpop(keys: Array<String>, timeoutSeconds: Double): Supplier<Pair<String, ByteString>?> = eager {
      this@FakeRedis.blpop(keys, timeoutSeconds)
    }

    override fun rpop(key: String, count: Int): Supplier<List<ByteString?>> = eager { this@FakeRedis.rpop(key, count) }

    override fun llen(key: String): Supplier<Long> = eager { this@FakeRedis.llen(key) }

    override fun rpop(key: String): Supplier<ByteString?> = eager { this@FakeRedis.rpop(key) }

    override fun lrange(key: String, start: Long, stop: Long): Supplier<List<ByteString?>> = eager {
      this@FakeRedis.lrange(key, start, stop)
    }

    override fun ltrim(key: String, start: Long, stop: Long): Supplier<Unit> = eager {
      this@FakeRedis.ltrim(key, start, stop)
    }

    override fun lrem(key: String, count: Long, element: ByteString): Supplier<Long> = eager {
      this@FakeRedis.lrem(key, count, element)
    }

    override fun rpoplpush(sourceKey: String, destinationKey: String): Supplier<ByteString?> = eager {
      this@FakeRedis.rpoplpush(sourceKey, destinationKey)
    }

    override fun exists(key: String): Supplier<Boolean> = eager { this@FakeRedis.exists(key) }

    override fun exists(vararg keys: String): Supplier<Long> = eager { this@FakeRedis.exists(*keys) }

    override fun persist(key: String): Supplier<Boolean> = eager { this@FakeRedis.persist(key) }

    override fun expire(key: String, seconds: Long): Supplier<Boolean> = eager { this@FakeRedis.expire(key, seconds) }

    override fun expireAt(key: String, timestampSeconds: Long): Supplier<Boolean> = eager {
      this@FakeRedis.expireAt(key, timestampSeconds)
    }

    override fun pExpire(key: String, milliseconds: Long): Supplier<Boolean> = eager {
      this@FakeRedis.pExpire(key, milliseconds)
    }

    override fun pExpireAt(key: String, timestampMilliseconds: Long): Supplier<Boolean> = eager {
      this@FakeRedis.pExpireAt(key, timestampMilliseconds)
    }

    override fun zadd(key: String, score: Double, member: String, vararg options: Redis.ZAddOptions): Supplier<Long> =
      eager {
        this@FakeRedis.zadd(key, score, member, *options)
      }

    override fun zadd(
      key: String,
      scoreMembers: Map<String, Double>,
      vararg options: Redis.ZAddOptions,
    ): Supplier<Long> = eager { this@FakeRedis.zadd(key, scoreMembers, *options) }

    override fun zscore(key: String, member: String): Supplier<Double?> = eager { this@FakeRedis.zscore(key, member) }

    override fun zrange(
      key: String,
      type: ZRangeType,
      start: ZRangeMarker,
      stop: ZRangeMarker,
      reverse: Boolean,
      limit: ZRangeLimit?,
    ): Supplier<List<ByteString?>> = eager { this@FakeRedis.zrange(key, type, start, stop, reverse, limit) }

    override fun zrangeWithScores(
      key: String,
      type: ZRangeType,
      start: ZRangeMarker,
      stop: ZRangeMarker,
      reverse: Boolean,
      limit: ZRangeLimit?,
    ): Supplier<List<Pair<ByteString?, Double>>> = eager {
      this@FakeRedis.zrangeWithScores(key, type, start, stop, reverse, limit)
    }

    override fun zrem(key: String, vararg members: String): Supplier<Long> = eager {
      this@FakeRedis.zrem(key, *members)
    }

    override fun zremRangeByRank(key: String, start: ZRangeRankMarker, stop: ZRangeRankMarker): Supplier<Long> = eager {
      this@FakeRedis.zremRangeByRank(key, start, stop)
    }

    override fun zremRangeByScore(key: String, start: ZRangeScoreMarker, stop: ZRangeScoreMarker): Supplier<Long> =
      eager {
        this@FakeRedis.zremRangeByScore(key, start, stop)
      }

    override fun zcard(key: String): Supplier<Long> = eager { this@FakeRedis.zcard(key) }

    override fun close() {
      // No-op.
    }
  }

  override fun close() {
    // no op
  }

  override fun subscribe(jedisPubSub: JedisPubSub, channel: String) {
    throw NotImplementedError("Fake client not implemented for this operation")
  }

  override fun publish(channel: String, message: String) {
    throw NotImplementedError("Fake client not implemented for this operation")
  }

  override fun flushAll() {
    keyValueStore.clear()
  }

  override fun flushDB() {
    flushAll()
  }

  private fun zaddInternal(key: String, score: Double, member: String, options: Array<out Redis.ZAddOptions>): Long {
    Redis.ZAddOptions.verify(options)
    var newFieldCount = 0L
    var elementsChanged = 0L
    val trackChange = options.contains(CH)

    if (!keyValueStore.containsKey(key)) {
      keyValueStore[key] = Value.SortedSet(data = sortedMapOf(), expiryInstant = Instant.MAX)
    }
    val sortedSet = keyValueStore.getTyped<Value.SortedSet>(key)!!.data
    var currentScore: Double? = null
    var exists = false

    for (entries in sortedSet.entries) {
      if (entries.value.contains(member)) {
        exists = true
        currentScore = entries.key
        break
      }
    }

    if (shouldUpdateScore(currentScore, score, exists, options)) {
      val previousScore = currentScore
      // Take the member out of its old bucket before putting it in the new one. Adding first and then scanning for the
      // member finds whichever bucket comes first in score order, which is the bucket just written whenever the member
      // is re-added at the score it already has or moved down into an occupied one — so the member ended up deleted, or
      // left at its old score, instead of placed.
      if (previousScore != null) {
        sortedSet[previousScore]?.let { old ->
          old.remove(member)
          if (old.isEmpty()) sortedSet.remove(previousScore)
        }
      } else {
        newFieldCount++
      }

      val scoreMembers = sortedSet[score] ?: hashSetOf<String>().also { sortedSet[score] = it }
      scoreMembers.add(member)
      elementsChanged++
    }

    if (trackChange) return elementsChanged
    return newFieldCount
  }

  /** If [exists], the [currentScore] will be present. If not, [currentScore] will be null */
  private fun shouldUpdateScore(
    currentScore: Double?,
    score: Double,
    exists: Boolean,
    zaddOptions: Array<out Redis.ZAddOptions>,
  ): Boolean {
    val options = zaddOptions.filter { it != CH }
    // default without any options
    if (options.isEmpty()) return true

    // all valid single options.
    if (
      (options.size == 1) &&
        (((options[0] == XX) && exists) ||
          ((options[0] == NX) && !exists) ||
          ((options[0] == LT) && ((exists && score < currentScore!!) || !exists)) ||
          ((options[0] == GT) && ((exists && score > currentScore!!) || !exists)))
    )
      return true

    // valid option combos
    // only two valid combos of two option are possible.
    // LT XX and GT XX

    // LT XX
    // for existing ones, the score should be less than the existing scores.
    // XX will prevent adding new ones.
    if (options.contains(LT) && options.contains(XX) && exists && score < currentScore!!) return true

    // GT XX
    // for existing ones, the score should be more than the existing scores.
    // XX will prevent adding new ones.
    if (options.contains(GT) && options.contains(XX) && exists && score > currentScore!!) return true

    return false
  }

  override fun zadd(key: String, score: Double, member: String, vararg options: Redis.ZAddOptions): Long {
    return zaddInternal(key, score, member, options)
  }

  override fun zadd(key: String, scoreMembers: Map<String, Double>, vararg options: Redis.ZAddOptions): Long {
    return scoreMembers.entries.sumOf { (member, score) -> zaddInternal(key, score, member, options) }
  }

  override fun zscore(key: String, member: String): Double? {
    val sortedSet = keyValueStore.getTyped<Value.SortedSet>(key) ?: return null

    var currentScore: Double? = null
    for (entries in sortedSet.data.entries) {
      if (entries.value.contains(member)) {
        currentScore = entries.key
        break
      }
    }

    return currentScore
  }

  override fun zrange(
    key: String,
    type: ZRangeType,
    start: ZRangeMarker,
    stop: ZRangeMarker,
    reverse: Boolean,
    limit: ZRangeLimit?,
  ): List<ByteString?> {
    return zrangeWithScores(key, type, start, stop, reverse, limit).map { (member, _) -> member }.toList()
  }

  override fun zrangeWithScores(
    key: String,
    type: ZRangeType,
    start: ZRangeMarker,
    stop: ZRangeMarker,
    reverse: Boolean,
    limit: ZRangeLimit?,
  ): List<Pair<ByteString?, Double>> {
    val sortedSet = keyValueStore.getTyped<Value.SortedSet>(key)?.data?.toSortedMap() ?: return listOf()

    val ansWithScore =
      when (type) {
        ZRangeType.INDEX ->
          zrangeByIndex(
            sortedSet = sortedSet,
            start = start as ZRangeIndexMarker,
            stop = stop as ZRangeIndexMarker,
            reverse = reverse,
          )

        ZRangeType.SCORE ->
          zrangeByScore(
            sortedSet = sortedSet,
            start = start as ZRangeScoreMarker,
            stop = stop as ZRangeScoreMarker,
            reverse = reverse,
            limit = limit,
          )
      }

    return ansWithScore
  }

  override fun zrem(key: String, vararg members: String): Long {
    val sortedSet = keyValueStore.getTyped<Value.SortedSet>(key)?.data ?: return 0

    var removed = 0L
    for (member in members) {
      // A member sits under exactly one score, so the first bucket holding it is the only one to touch. A member named
      // twice is removed once, which is what Redis counts too.
      for (entry in sortedSet.entries) {
        if (entry.value.remove(member)) {
          removed++
          break
        }
      }
    }

    // Redis keeps neither an empty score nor an empty sorted set: the key goes away with its last member. The stored
    // value is mutated rather than replaced so this does not disturb the key's expiry.
    for (score in sortedSet.keys.toList()) {
      if (sortedSet.getValue(score).isEmpty()) sortedSet.remove(score)
    }
    if (sortedSet.isEmpty()) keyValueStore.remove(key)

    return removed
  }

  override fun zremRangeByRank(key: String, start: ZRangeRankMarker, stop: ZRangeRankMarker): Long {
    val sortedSet = keyValueStore.getTyped<Value.SortedSet>(key)?.data ?: return 0

    val (minInt, maxInt, _) = getMinMaxIndex(sortedSet, start.longValue, stop.longValue)
    if (minInt > maxInt) return 0

    var ctr = 0L
    var removed = 0L
    // The stored value is mutated rather than replaced so the key keeps its expiry. Rebuilding it dropped the TTL, so a
    // trimmed set outlived the deadline its writer had set.
    for (score in sortedSet.keys.toList()) {
      val members = sortedSet.getValue(score)
      for (member in members.sorted()) {
        if (ctr in minInt..maxInt) {
          members.remove(member)
          removed++
        }
        ctr++
      }
      if (members.isEmpty()) sortedSet.remove(score)
    }
    // Redis keeps neither an empty score nor an empty sorted set: the key goes away with its last member.
    if (sortedSet.isEmpty()) keyValueStore.remove(key)

    return removed
  }

  override fun zremRangeByScore(key: String, start: ZRangeScoreMarker, stop: ZRangeScoreMarker): Long {
    val sortedSet = keyValueStore.getTyped<Value.SortedSet>(key)?.data ?: return 0

    val minDouble = start.bound()
    val maxDouble = stop.bound()
    if (minDouble > maxDouble) return 0

    // Matches how zrangeByScore reads the same markers, so a range that selects members there removes exactly those.
    fun Double.inRange(): Boolean {
      val aboveStart = if (start.included) this >= minDouble else this > minDouble
      val belowStop = if (stop.included) this <= maxDouble else this < maxDouble
      return aboveStart && belowStop
    }

    var removed = 0L
    // Every member under one score shares that score, so a bucket is wholly in the range or wholly out of it.
    for (score in sortedSet.keys.toList()) {
      if (!score.inRange()) continue
      removed += sortedSet.getValue(score).size
      sortedSet.remove(score)
    }
    if (sortedSet.isEmpty()) keyValueStore.remove(key)

    return removed
  }

  override fun zcard(key: String): Long {
    val sortedSet = keyValueStore.getTyped<Value.SortedSet>(key)?.data ?: return 0
    var length = 0L
    sortedSet.values.forEach { length += it.size }
    return length
  }

  private fun getMinMaxIndex(
    sortedSet: SortedMap<Double, HashSet<String>>,
    start: Long,
    stop: Long,
  ): Triple<Long, Long, Long> {
    var min = start
    var max = stop
    var length = 0L
    sortedSet.values.forEach { length += it.size }

    // Redis offsets a negative index against the length exactly once. An index that is still negative afterwards stays
    // negative, and a start past the end stays past it, so that either one leaves min > max and the range comes out
    // empty. Clamping both ends into the set instead turned every empty range into a one-member range, which silently
    // removed the first or last member of the set.
    if (min < 0) min += length
    if (min < 0) min = 0

    if (max < 0) max += length
    if (max > length - 1) max = length - 1

    return Triple(min, max, length)
  }

  private fun zrangeByIndex(
    sortedSet: SortedMap<Double, HashSet<String>>,
    start: ZRangeIndexMarker,
    stop: ZRangeIndexMarker,
    reverse: Boolean,
  ): List<Pair<ByteString?, Double>> {
    val scores = if (!reverse) sortedSet.keys.toList() else sortedSet.keys.toList().reversed()
    val (minInt, maxInt) = getMinMaxIndex(sortedSet, start.intValue.toLong(), stop.intValue.toLong())

    if (minInt > maxInt) return listOf()

    val ans = mutableListOf<Pair<ByteString?, Double>>()
    var ctr = 0

    for (idx in scores.indices) {
      val score = scores[idx]
      var members = sortedSet[score]!!.sorted()
      if (reverse) members = members.reversed()
      for (member in members) {
        if (ctr in minInt..maxInt) {
          ans.add(Pair(member.encodeUtf8(), score))
        }
        ctr++
      }
    }

    return ans
  }

  /**
   * The score this marker actually bounds. A [ZRangeScoreMarker] spells its open ends as [Double.MAX_VALUE] and
   * [Double.MIN_VALUE], which [ZRangeScoreMarker.toString] sends to a real server as "+inf" and "-inf". Comparing the
   * raw value instead would read the lower sentinel as 4.9E-324 — a tiny *positive* number — and so leave behind every
   * member scoring at or below zero.
   */
  private fun ZRangeScoreMarker.bound(): Double =
    when (doubleValue) {
      Double.MAX_VALUE -> Double.POSITIVE_INFINITY
      Double.MIN_VALUE -> Double.NEGATIVE_INFINITY
      else -> doubleValue
    }

  private fun zrangeByScore(
    sortedSet: SortedMap<Double, HashSet<String>>,
    start: ZRangeScoreMarker,
    stop: ZRangeScoreMarker,
    reverse: Boolean,
    limit: ZRangeLimit?,
  ): List<Pair<ByteString?, Double>> {
    val scores = if (!reverse) sortedSet.keys.toList() else sortedSet.keys.toList().reversed()
    val minDouble = start.bound()
    val maxDouble = stop.bound()

    if (minDouble > maxDouble) return listOf()

    fun Double.cmp(): Boolean {
      var ans = if (start.included) this >= minDouble else this > minDouble

      ans = if (stop.included) ans && this <= maxDouble else ans && this < maxDouble

      return ans
    }

    val ans = mutableListOf<Pair<ByteString?, Double>>()
    var ctr = 0
    var offset = 0
    var count = Int.MAX_VALUE
    if (limit != null) {
      offset = limit.offset
      count = limit.count
    }

    if (count < 0) count = Int.MAX_VALUE

    val filteredScores = scores.filter { it.cmp() }

    for (score in filteredScores) {
      var members = sortedSet[score]!!.sorted()
      if (reverse) members = members.reversed()
      for (member in members) {
        if (ctr >= offset && ans.size < count) {
          ans.add(Pair(member.encodeUtf8(), score))
        }
        ctr++
        if (ans.size == count) break
      }
      if (ans.size == count) break
    }
    return ans
  }
}
