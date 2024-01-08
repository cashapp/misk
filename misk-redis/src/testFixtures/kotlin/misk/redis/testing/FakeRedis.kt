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
import misk.redis.Redis.ZAddOptions.XX
import misk.redis.Redis.ZAddOptions.NX
import misk.redis.Redis.ZAddOptions.LT
import misk.redis.Redis.ZAddOptions.GT
import misk.redis.Redis.ZAddOptions.CH
import redis.clients.jedis.exceptions.JedisDataException
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
 *  - FakeRedis does not currently support [Redis.pipelined] requests or [Redis.multi] transactions
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
    ConcurrentHashMap<String, Value<HashMap<Double, HashSet<String>>>>()

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

  override fun pipelined(): Pipeline {
    throw NotImplementedError("Fake client not implemented for this operation")
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

  private fun zadd(
    key: String,
    score: Double,
    member: String,
    options: Set<Redis.ZAddOptions>,
  ): Long {
    verifyZAddOptions(options)
    var newFieldCount = 0L
    var elementsChanged = 0L
    val trackChange = options.contains(CH)

    if (!sortedSetKeyValueStore.containsKey(key)) {
      sortedSetKeyValueStore[key] = Value(data = hashMapOf(), expiryInstant = Instant.MAX)
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
      sortedSet[score] = scoreMembers

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
    zaddOptions: Set<Redis.ZAddOptions>
  ) : Boolean {
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

  private fun verifyZAddOptions(options: Set<Redis.ZAddOptions>) {
    // zadd syntax
    // zadd key [NX|XX] [GT|LT] CH score member [score member ...]

    // NX and XX are mutually exclusive
    if ((options.contains(NX) && options.contains(XX))) {
      throw JedisDataException("ERR XX and NX options at the same time are not compatible")
    }

    // GT, LT, NX are mutually exclusive
    if ((options.contains(NX) && options.contains(LT)) ||
      (options.contains(NX) && options.contains(GT)) ||
      (options.contains(GT) && options.contains(LT))) {
      throw JedisDataException("ERR GT, LT, and/or NX options at the same time are not compatible")
    }
  }

  override fun zadd(
    key: String,
    score: Double,
    member: String,
    vararg options: Redis.ZAddOptions,
  ): Long {
    return zadd(key, score, member, options.toSet())
  }

  override fun zadd(
    key: String,
    scoreMembers: Map<String, Double>,
    vararg options: Redis.ZAddOptions
  ): Long {
    return scoreMembers.entries.sumOf {
      (member, score) ->
      zadd(key, score, member, options.toSet())
    }
  }

  override fun zscore(key: String, member: String): Double? {
    if (sortedSetKeyValueStore[key] == null) return null

    var currentScore:Double? = null
    for (entries in sortedSetKeyValueStore[key]!!.data.entries) {
      if (entries.value.contains(member)) {
        currentScore = entries.key
        break
      }
    }

    return currentScore
  }
  }
}
