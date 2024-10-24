package misk.redis.testing

import misk.redis.Redis
import misk.redis.checkHrandFieldCount
import okio.ByteString
import okio.ByteString.Companion.encode
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.args.ListDirection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import jakarta.inject.Inject
import misk.redis.DeferredRedis
import misk.redis.Redis.ZAddOptions.XX
import misk.redis.Redis.ZAddOptions.NX
import misk.redis.Redis.ZAddOptions.LT
import misk.redis.Redis.ZAddOptions.GT
import misk.redis.Redis.ZAddOptions.CH
import misk.redis.Redis.ZRangeIndexMarker
import misk.redis.Redis.ZRangeLimit
import misk.redis.Redis.ZRangeMarker
import misk.redis.Redis.ZRangeRankMarker
import misk.redis.Redis.ZRangeScoreMarker
import misk.redis.Redis.ZRangeType
import okio.ByteString.Companion.encodeUtf8
import org.apache.commons.io.FilenameUtils
import java.util.SortedMap
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * An in-memory key-value store which closely mimics [misk.redis.RealRedis].
 *
 * This should be used if:
 *  - It is undesirable to start an actual Redis instance via [DockerRedis] in test.
 *  - You need fine-grained control over randomness in tests
 *  - You need fine-grained control over key-expiry in tests
 *
 * Caveats:
 *  - FakeRedis does not currently support [Redis.multi] transactions
 */
class FakeRedis @Inject constructor(
  private val clock: Clock,
  @ForFakeRedis private val random: Random,
) : Redis {
  /** The value type stored in our key-value store. */
  private data class Value<T>(val data: T, var expiryInstant: Instant)

  /** Acts as the Redis key-value store. */
  private val keyValueStore = ConcurrentHashMap<String, Value<ByteString>>()

  /** A nested hash map for hash operations. */
  private val hKeyValueStore =
    ConcurrentHashMap<String, Value<ConcurrentHashMap<String, ByteString>>>()

  /**
   * Note: Redis sorted set actually orders by value. It is quite complex to implement it here.
   * In this Fake Redis implementation which is generally used for testing, we have simply used a
   * HashMap to key score->members. So any sorting based on values will have to be handled in the
   * implementation of the functions for this sorted set.
   */
  private val sortedSetKeyValueStore =
    ConcurrentHashMap<String, Value<SortedMap<Double, HashSet<String>>>>()

  /** A hash map for list operations. */
  private val lKeyValueStore = ConcurrentHashMap<String, Value<List<ByteString>>>()

  @Synchronized
  override fun del(key: String): Boolean {
    if (!keyValueStore.containsKey(key)) {
      return false
    }
    return keyValueStore.remove(key) != null
  }

  @Synchronized
  override fun del(vararg keys: String): Int = keys.count { del(it) }

  @Synchronized
  override fun mget(vararg keys: String): List<ByteString?> = keys.map { get(it) }

  @Synchronized
  override fun mset(vararg keyValues: ByteString) {
    require(keyValues.size % 2 == 0) { "Wrong number of arguments to mset" }

    (keyValues.indices step 2).forEach {
      set(keyValues[it].utf8(), keyValues[it + 1])
    }
  }

  @Synchronized
  override fun get(key: String): ByteString? {
    val value = keyValueStore[key] ?: return null

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      keyValueStore.remove(key)
      return null
    }
    return value.data
  }

  @Synchronized
  override fun getDel(key: String): ByteString? {
    val value = get(key);
    keyValueStore.remove(key);
    return value
  }

  @Synchronized
  override fun hdel(key: String, vararg fields: String): Long {
    val value = hKeyValueStore[key] ?: return 0L

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      hKeyValueStore.remove(key)
      return 0L
    }

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
    val value = hKeyValueStore[key] ?: return null

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      hKeyValueStore.remove(key)
      return null
    }
    return value.data[field]
  }

  @Synchronized
  override fun hgetAll(key: String): Map<String, ByteString> {
    val value = hKeyValueStore[key] ?: return emptyMap()

    // Check if the key has expired
    if (clock.instant() >= value.expiryInstant) {
      hKeyValueStore.remove(key)
      return emptyMap()
    }
    return value.data.mapValues { it.value }
  }

  @Synchronized
  override fun hlen(key: String): Long = hKeyValueStore[key]?.data?.size?.toLong() ?: 0L

  @Synchronized
  override fun hmget(key: String, vararg fields: String): List<ByteString?> {
    val hash: Map<String, ByteString> = hKeyValueStore[key]?.data ?: emptyMap()
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
    keyValueStore[key] = Value(data = value, expiryInstant = Instant.MAX)
  }

  @Synchronized
  override fun set(key: String, expiryDuration: Duration, value: ByteString) {
    keyValueStore[key] = Value(
      data = value,
      expiryInstant = clock.instant().plusSeconds(expiryDuration.seconds)
    )
  }

  @Synchronized
  override fun setnx(key: String, value: ByteString): Boolean {
    return setWithExpiry(key, value, Instant.MAX)
  }

  @Synchronized
  override fun setnx(key: String, expiryDuration: Duration, value: ByteString): Boolean {
    return setWithExpiry(key, value, clock.instant().plusSeconds(expiryDuration.seconds))
  }

  private fun setWithExpiry(key: String, value: ByteString, expiryInstant: Instant): Boolean {
    return keyValueStore.putIfAbsent(key, Value(data = value, expiryInstant)) == null
  }

  @Synchronized
  override fun hset(key: String, field: String, value: ByteString): Long {
    if (!hKeyValueStore.containsKey(key)) {
      hKeyValueStore[key] = Value(data = ConcurrentHashMap(), expiryInstant = Instant.MAX)
    }
    val newFieldCount = if (hKeyValueStore[key]!!.data[field] != null) 0L else 1L
    hKeyValueStore[key]!!.data[field] = value
    return newFieldCount
  }

  @Synchronized
  override fun hset(key: String, hash: Map<String, ByteString>): Long {
    return hash.entries.sumOf { (field, value) -> hset(key, field, value) }
  }

  @Synchronized
  override fun incr(key: String): Long = incrBy(key, 1)

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
    timeoutSeconds: Double
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
  override fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString? {
    val sourceList = lKeyValueStore[sourceKey]?.data?.toMutableList() ?: return null
    val sourceValue = when (from) {
      ListDirection.LEFT -> sourceList.removeFirst()
      ListDirection.RIGHT -> sourceList.removeLast()
    }
    lKeyValueStore[sourceKey] = Value(data = sourceList, expiryInstant = Instant.MAX)

    val destinationList = lKeyValueStore[destinationKey]?.data?.toMutableList() ?: mutableListOf()
    when (to) {
      ListDirection.LEFT -> destinationList.add(index = 0, element = sourceValue)
      ListDirection.RIGHT -> destinationList.add(element = sourceValue)
    }
    lKeyValueStore[destinationKey] = Value(data = destinationList, expiryInstant = Instant.MAX)
    return sourceValue
  }

  @Synchronized
  override fun lpush(key: String, vararg elements: ByteString): Long {
    val updated = lKeyValueStore[key]?.data?.toMutableList() ?: mutableListOf()
    for (element in elements) {
      updated.add(0, element)
    }
    lKeyValueStore[key] = Value(data = updated, expiryInstant = Instant.MAX)
    return updated.size.toLong()
  }

  @Synchronized
  override fun rpush(key: String, vararg elements: ByteString): Long {
    val updated = lKeyValueStore[key]?.data?.toMutableList() ?: mutableListOf()
    updated.addAll(elements)
    lKeyValueStore[key] = Value(data = updated, expiryInstant = Instant.MAX)
    return updated.size.toLong()
  }

  @Synchronized
  override fun lpop(key: String, count: Int): List<ByteString?> {
    val value = lKeyValueStore[key] ?: Value(emptyList(), clock.instant())
    if (clock.instant() >= value.expiryInstant) {
      return emptyList()
    }
    val result = with(value) {
      data.subList(0, min(data.size, count)).toList()
    }
    lKeyValueStore[key] = value.copy(data = value.data.drop(count))
    return result
  }

  @Synchronized
  override fun lpop(key: String): ByteString? = lpop(key, count = 1).firstOrNull()

  @Synchronized
  override fun rpop(key: String, count: Int): List<ByteString?> {
    val value = lKeyValueStore[key] ?: Value(emptyList(), clock.instant())
    if (clock.instant() >= value.expiryInstant) {
      return emptyList()
    }
    val result = with(value) {
      data.takeLast(min(data.size, count)).asReversed()
    }
    lKeyValueStore[key] = value.copy(data = value.data.dropLast(count))
    return result
  }

  @Synchronized
  override fun llen(key: String): Long {
    return lKeyValueStore[key]?.data?.size?.toLong() ?: 0L
  }

  override fun rpop(key: String): ByteString? = rpop(key, count = 1).firstOrNull()

  @Synchronized
  override fun lrange(key: String, start: Long, stop: Long): List<ByteString?> {
    val list = lKeyValueStore[key]?.data ?: return emptyList()
    if (start >= list.size) return emptyList()

    // Redis allows negative values starting from the end of the list.
    val first = if (start < 0) list.size + start else start
    val last = if (stop < 0) list.size + stop else stop

    // Redis is inclusive on both sides; Kotlin only on start.
    return list.subList(max(0, first.toInt()), min(last.toInt() + 1, list.size))
  }

  @Synchronized
  override fun lrem(key: String, count: Long, element: ByteString): Long {
    val value = lKeyValueStore[key] ?: return 0L
    if (clock.instant() >= value.expiryInstant) {
      lKeyValueStore.remove(key)
      return 0L
    }

    val list = value.data.toMutableList()
    var totalCount = count
    val iterList = if (count < 0) {
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
    lKeyValueStore[key] = Value(data = list, expiryInstant = Instant.MAX)

    return deleteCount
  }

  @Synchronized
  override fun rpoplpush(sourceKey: String, destinationKey: String) = lmove(
    sourceKey = sourceKey,
    destinationKey = destinationKey,
    from = ListDirection.RIGHT,
    to = ListDirection.LEFT
  )

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
  override fun pExpire(key: String, milliseconds: Long): Boolean =
    pExpireAt(key, clock.millis().plus(milliseconds))

  @Synchronized
  override fun pExpireAt(key: String, timestampMilliseconds: Long): Boolean {
    val value = keyValueStore[key]
    val hValue = hKeyValueStore[key]
    val lValue = lKeyValueStore[key]
    val expiresAt = Instant.ofEpochMilli(timestampMilliseconds)

    when {
      value != null -> value.expiryInstant = expiresAt
      hValue != null -> hValue.expiryInstant = expiresAt
      lValue != null -> lValue.expiryInstant = expiresAt
      else -> return false
    }
    return true
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
   * A poor implementation of pipelining for testing purposes.
   * Unlike a real pipeline, this does not queue commands.
   */
  inner class FakePipelinedRedis : DeferredRedis {
    override fun del(key: String): Supplier<Boolean> = Supplier { this@FakeRedis.del(key) }

    override fun del(vararg keys: String): Supplier<Int> = Supplier { this@FakeRedis.del(*keys) }

    override fun mget(vararg keys: String): Supplier<List<ByteString?>> = Supplier {
      this@FakeRedis.mget(*keys)
    }

    override fun mset(vararg keyValues: ByteString): Supplier<Unit> = Supplier {
      this@FakeRedis.mset(*keyValues)
    }

    override fun get(key: String): Supplier<ByteString?> = Supplier { this@FakeRedis[key] }

    override fun getDel(key: String): Supplier<ByteString?> = Supplier {
      this@FakeRedis.getDel(key)
    }

    override fun hdel(key: String, vararg fields: String): Supplier<Long> = Supplier {
      this@FakeRedis.hdel(key, *fields)
    }

    override fun hget(key: String, field: String): Supplier<ByteString?> = Supplier {
      this@FakeRedis.hget(key, field)
    }

    override fun hgetAll(key: String): Supplier<Map<String, ByteString>?> = Supplier {
      this@FakeRedis.hgetAll(key)
    }

    override fun hlen(key: String): Supplier<Long> = Supplier {
      this@FakeRedis.hlen(key)
    }

    override fun hmget(
      key: String,
      vararg fields: String
    ): Supplier<List<ByteString?>> = Supplier {
      this@FakeRedis.hmget(key, *fields)
    }

    override fun hincrBy(
      key: String,
      field: String,
      increment: Long
    ): Supplier<Long> = Supplier {
      this@FakeRedis.hincrBy(key, field, increment)
    }

    override fun hrandFieldWithValues(
      key: String,
      count: Long
    ): Supplier<Map<String, ByteString>?> = Supplier {
      this@FakeRedis.hrandFieldWithValues(key, count)
    }

    override fun hrandField(key: String, count: Long): Supplier<List<String>> = Supplier {
      this@FakeRedis.hrandField(key, count)
    }

    override fun set(key: String, value: ByteString, expiryDuration: Duration?): Supplier<Unit> = Supplier {
      if (expiryDuration == null) {
        this@FakeRedis[key] = value
      } else {
        this@FakeRedis[key, expiryDuration] = value
      }
    }


    override fun setnx(
      key: String,
      value: ByteString,
      expiryDuration: Duration?
    ): Supplier<Boolean> = Supplier {
      if (expiryDuration == null) {
        this@FakeRedis.setnx(key, value)
      } else {
        this@FakeRedis.setnx(key, expiryDuration, value)
      }
    }

    override fun hset(key: String, field: String, value: ByteString): Supplier<Long> = Supplier {
      this@FakeRedis.hset(key, field, value)
    }

    override fun hset(key: String, hash: Map<String, ByteString>): Supplier<Long> = Supplier {
      this@FakeRedis.hset(key, hash)
    }

    override fun incr(key: String): Supplier<Long> = Supplier {
      this@FakeRedis.incr(key)
    }

    override fun incrBy(key: String, increment: Long): Supplier<Long> = Supplier {
      this@FakeRedis.incrBy(key, increment)
    }

    override fun blmove(
      sourceKey: String,
      destinationKey: String,
      from: ListDirection,
      to: ListDirection,
      timeoutSeconds: Double
    ): Supplier<ByteString?> = Supplier {
      this@FakeRedis.blmove(sourceKey, destinationKey, from, to, timeoutSeconds)
    }

    override fun brpoplpush(
      sourceKey: String,
      destinationKey: String,
      timeoutSeconds: Int
    ): Supplier<ByteString?> = Supplier {
      this@FakeRedis.brpoplpush(sourceKey, destinationKey, timeoutSeconds)
    }

    override fun lmove(
      sourceKey: String,
      destinationKey: String,
      from: ListDirection,
      to: ListDirection
    ): Supplier<ByteString?> = Supplier {
      this@FakeRedis.lmove(sourceKey, destinationKey, from, to)
    }

    override fun lpush(key: String, vararg elements: ByteString): Supplier<Long> = Supplier {
      this@FakeRedis.lpush(key, *elements)
    }

    override fun rpush(key: String, vararg elements: ByteString): Supplier<Long> = Supplier {
      this@FakeRedis.rpush(key, *elements)
    }

    override fun lpop(key: String, count: Int): Supplier<List<ByteString?>> = Supplier {
      this@FakeRedis.lpop(key, count)
    }

    override fun lpop(key: String): Supplier<ByteString?> = Supplier {
      this@FakeRedis.lpop(key)
    }

    override fun rpop(key: String, count: Int): Supplier<List<ByteString?>> = Supplier {
      this@FakeRedis.rpop(key, count)
    }

    override fun llen(key: String): Supplier<Long> = Supplier {
      this@FakeRedis.llen(key)
    }

    override fun rpop(key: String): Supplier<ByteString?> = Supplier {
      this@FakeRedis.rpop(key)
    }

    override fun lrange(
      key: String,
      start: Long,
      stop: Long
    ): Supplier<List<ByteString?>> = Supplier {
      this@FakeRedis.lrange(key, start, stop)
    }

    override fun lrem(key: String, count: Long, element: ByteString): Supplier<Long> = Supplier {
      this@FakeRedis.lrem(key, count, element)
    }

    override fun rpoplpush(
      sourceKey: String,
      destinationKey: String
    ): Supplier<ByteString?> = Supplier {
      this@FakeRedis.rpoplpush(sourceKey, destinationKey)
    }

    override fun expire(key: String, seconds: Long): Supplier<Boolean> = Supplier {
      this@FakeRedis.expire(key, seconds)
    }

    override fun expireAt(key: String, timestampSeconds: Long): Supplier<Boolean> = Supplier {
      this@FakeRedis.expireAt(key, timestampSeconds)
    }

    override fun pExpire(key: String, milliseconds: Long): Supplier<Boolean> = Supplier {
      this@FakeRedis.pExpire(key, milliseconds)
    }

    override fun pExpireAt(
      key: String,
      timestampMilliseconds: Long
    ): Supplier<Boolean> = Supplier {
      this@FakeRedis.pExpireAt(key, timestampMilliseconds)
    }

    override fun zadd(
      key: String,
      score: Double,
      member: String,
      vararg options: Redis.ZAddOptions
    ): Supplier<Long> = Supplier {
      this@FakeRedis.zadd(key, score, member, *options)
    }

    override fun zadd(
      key: String,
      scoreMembers: Map<String, Double>,
      vararg options: Redis.ZAddOptions
    ): Supplier<Long> = Supplier {
      this@FakeRedis.zadd(key, scoreMembers, *options)
    }

    override fun zscore(key: String, member: String): Supplier<Double?> = Supplier {
      this@FakeRedis.zscore(key, member)
    }

    override fun zrange(
      key: String,
      type: ZRangeType,
      start: ZRangeMarker,
      stop: ZRangeMarker,
      reverse: Boolean,
      limit: ZRangeLimit?
    ): Supplier<List<ByteString?>> = Supplier {
      this@FakeRedis.zrange(key, type, start, stop, reverse, limit)
    }

    override fun zrangeWithScores(
      key: String,
      type: ZRangeType,
      start: ZRangeMarker,
      stop: ZRangeMarker,
      reverse: Boolean,
      limit: ZRangeLimit?
    ): Supplier<List<Pair<ByteString?, Double>>> = Supplier {
      this@FakeRedis.zrangeWithScores(key, type, start, stop, reverse, limit)
    }

    override fun zremRangeByRank(
      key: String,
      start: ZRangeRankMarker,
      stop: ZRangeRankMarker
    ): Supplier<Long> = Supplier {
      this@FakeRedis.zremRangeByRank(key, start, stop)
    }

    override fun zcard(key: String): Supplier<Long> = Supplier {
      this@FakeRedis.zcard(key)
    }

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
    hKeyValueStore.clear()
    lKeyValueStore.clear()
  }

  private fun zaddInternal(
    key: String,
    score: Double,
    member: String,
    options: Array<out Redis.ZAddOptions>,
  ): Long {
    Redis.ZAddOptions.verify(options)
    var newFieldCount = 0L
    var elementsChanged = 0L
    val trackChange = options.contains(CH)

    if (!sortedSetKeyValueStore.containsKey(key)) {
      sortedSetKeyValueStore[key] = Value(data = sortedMapOf(), expiryInstant = Instant.MAX)
    }
    val sortedSet = sortedSetKeyValueStore[key]!!.data
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
      val scoreMembers = sortedSet[score] ?: hashSetOf()
      scoreMembers.add(member)
      // remove from list of score if it exists
      if (!exists) {
        newFieldCount++
      } else {
        for (entries in sortedSet.entries) {
          if (entries.value.contains(member)) {
            entries.value.remove(member)
            break
          }
        }
      }

      sortedSet[score] = scoreMembers
      elementsChanged++
    }

    if (trackChange) return elementsChanged
    return newFieldCount
  }

  /**
   * If [exists], the [currentScore] will be present. If not, [currentScore] will be null
   */
  private fun shouldUpdateScore(
    currentScore: Double?,
    score: Double,
    exists: Boolean,
    zaddOptions: Array<out Redis.ZAddOptions>
  ): Boolean {
    val options = zaddOptions.filter { it != CH }
    // default without any options
    if (options.isEmpty()) return true

    // all valid single options.
    if ((options.size == 1)
      && (((options[0] == XX) && exists)
        || ((options[0] == NX) && !exists)
        || ((options[0] == LT) && ((exists && score < currentScore!!) || !exists))
        || ((options[0] == GT) && ((exists && score > currentScore!!) || !exists)))
    ) return true

    // valid option combos
    // only two valid combos of two option are possible.
    // LT XX and GT XX

    // LT XX
    // for existing ones, the score should be less than the existing scores.
    // XX will prevent adding new ones.
    if (options.contains(LT) && options.contains(XX) && exists
      && score < currentScore!!) return true

    // GT XX
    // for existing ones, the score should be more than the existing scores.
    // XX will prevent adding new ones.
    if (options.contains(GT) && options.contains(XX) && exists
      && score > currentScore!!) return true

    return false
  }

  override fun zadd(
    key: String,
    score: Double,
    member: String,
    vararg options: Redis.ZAddOptions,
  ): Long {
    return zaddInternal(key, score, member, options)
  }

  override fun zadd(
    key: String,
    scoreMembers: Map<String, Double>,
    vararg options: Redis.ZAddOptions
  ): Long {
    return scoreMembers.entries.sumOf { (member, score) ->
      zaddInternal(key, score, member, options)
    }
  }

  override fun zscore(key: String, member: String): Double? {
    if (sortedSetKeyValueStore[key] == null) return null

    var currentScore: Double? = null
    for (entries in sortedSetKeyValueStore[key]!!.data.entries) {
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
    return zrangeWithScores(key, type, start, stop, reverse, limit)
      .map { (member, _) -> member }.toList()
  }

  override fun zrangeWithScores(
    key: String,
    type: ZRangeType,
    start: ZRangeMarker,
    stop: ZRangeMarker,
    reverse: Boolean,
    limit: ZRangeLimit?,
  ): List<Pair<ByteString?, Double>> {
    val sortedSet = sortedSetKeyValueStore[key]?.data?.toSortedMap() ?: return listOf()

    val ansWithScore = when (type) {
      ZRangeType.INDEX ->
        zrangeByIndex(
          sortedSet = sortedSet,
          start = start as ZRangeIndexMarker,
          stop = stop as ZRangeIndexMarker,
          reverse = reverse
        )

      ZRangeType.SCORE ->
        zrangeByScore(
          sortedSet = sortedSet,
          start = start as ZRangeScoreMarker,
          stop = stop as ZRangeScoreMarker,
          reverse = reverse,
          limit = limit
        )
    }

    return ansWithScore
  }

  override fun zremRangeByRank(
    key: String,
    start: ZRangeRankMarker,
    stop: ZRangeRankMarker,
  ): Long {
    val sortedSet = sortedSetKeyValueStore[key]?.data ?: return 0
    val scores = sortedSet.keys.toList()

    val (minInt, maxInt, length) = getMinMaxIndex(sortedSet, start.longValue, stop.longValue)

    if (minInt > maxInt) return 0

    var ctr = 0
    var added = 0

    val newSortedSet: SortedMap<Double, HashSet<String>> = sortedMapOf()

    for (idx in scores.indices) {
      val score = scores[idx]
      val members = sortedSet[score]!!.sorted()

      val newMembers = hashSetOf<String>()
      for (member in members) {
        if (ctr !in minInt..maxInt) {
          newMembers.add(member)
          added++
        }
        ctr++
      }
      if (newMembers.isNotEmpty()) newSortedSet[score] = newMembers
    }

    sortedSetKeyValueStore[key] = Value(data = newSortedSet, expiryInstant = Instant.MAX)

    return (length - added)
  }

  override fun zcard(
    key: String
  ): Long {
    val sortedSet = sortedSetKeyValueStore[key]?.data ?: return 0
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

    if (min < -length) min = -length
    if (min < 0) min += length
    if (min > length - 1) min = length - 1

    if (max < -length) max = -length
    if (max < 0) max += length
    if (max > length - 1) max = length - 1

    return Triple(min, max, length)
  }

  private fun zrangeByIndex(
    sortedSet: SortedMap<Double, HashSet<String>>,
    start: ZRangeIndexMarker,
    stop: ZRangeIndexMarker,
    reverse: Boolean
  ): List<Pair<ByteString?, Double>> {
    val scores = if (!reverse) sortedSet.keys.toList() else sortedSet.keys.toList().reversed()
    val (minInt, maxInt) =
      getMinMaxIndex(sortedSet, start.intValue.toLong(), stop.intValue.toLong())

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

  private fun zrangeByScore(
    sortedSet: SortedMap<Double, HashSet<String>>,
    start: ZRangeScoreMarker,
    stop: ZRangeScoreMarker,
    reverse: Boolean,
    limit: ZRangeLimit?
  ): List<Pair<ByteString?, Double>> {
    val scores = if (!reverse) sortedSet.keys.toList() else sortedSet.keys.toList().reversed()
    val minDouble = start.value as Double
    val maxDouble = stop.value as Double

    if (minDouble > maxDouble) return listOf()

    fun Double.cmp(): Boolean {
      var ans = if (start.included) this >= minDouble
      else this > minDouble

      ans = if (stop.included) ans && this <= maxDouble
      else ans && this < maxDouble

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
