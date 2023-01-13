package misk.redis

import okio.ByteString
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import redis.clients.jedis.args.ListDirection
import java.time.Duration

/** A Redis client. */
interface Redis {
  /**
   * Deletes a single key.
   *
   * @param key the key to delete
   * @return false if the key was not deleted, true if the key was deleted
   */
  fun del(key: String): Boolean

  /**
   * Deletes multiple keys.
   *
   * @param keys the keys to delete
   * @return 0 if none of the keys were deleted, otherwise a positive integer
   *         representing the number of keys that were deleted
   */
  fun del(vararg keys: String): Int

  /**
   * Retrieves the values for the given list of keys.
   *
   * @param keys the keys to retrieve
   * @return a list of String in the same order as the specified list of keys.
   * For each key, a value will be returned if a key was found, otherwise null is returned.
   */
  fun mget(vararg keys: String): List<ByteString?>

  /**
   * Sets the key value pairs.
   *
   * @param keyValues the list of keys and values in alternating order.
   */
  fun mset(vararg keyValues: ByteString)

  /**
   * Retrieves the value for the given key as a [ByteString].
   *
   * @param key the key to retrieve
   * @return a [ByteString] if the key was found, null if the key was not found
   */
  operator fun get(key: String): ByteString?

  /**
   * Delete one or more hash fields
   *
   * @param key the key for which to delete fields
   * @param fields the specific fields to delete
   * @return If the field was present in the hash it is deleted and 1 is returned, otherwise 0 is
   * returned and no operation is performed.
   */
  fun hdel(key: String, vararg fields: String): Long

  /**
   * Retrieves the value for the given key and field as a [ByteString].
   *
   * @param key the key
   * @param field the field
   * @return a [ByteString] if the key/field combination was found, null if not found
   */
  fun hget(key: String, field: String): ByteString?

  /**
   * Retrieves all the fields and associated values for the given key. Returns null if nothing
   * found.
   *
   * @param key the key
   * @return a Map<String, ByteString> of the fields to their associated values
   */
  fun hgetAll(key: String): Map<String, ByteString>?

  /**
   * Retrieve the values associated to the specified fields.
   *
   * If some specified fields do not exist, nil values are returned. Non-existing keys are
   * considered like empty hashes.
   *
   * @param key the key
   * @param fields the specific fields to retrieve
   * @return a List<ByteString?> of the values for the specific fields requested,
   * in the same order of the request. Null for missing fields
   */
  fun hmget(key: String, vararg fields: String): List<ByteString?>

  /**
   * Increments the number stored at [field] in the hash stored at [key] by [increment]. If [key]
   * does not exist, a new key holding a hash is created. If [field] does not exist the value is
   * set to 0 before the operation is performed.
   *
   * @param key the key.
   * @param field the field.
   * @return the value at [field] after the increment operation.
   */
  fun hincrBy(key: String, field: String, increment: Long): Long

  /**
   * Randomly selects [count] fields and values from the hash stored at [key].
   *
   * NB: Implementations using Jedis 4 or seeking to emulate Jedis should use [checkHrandFieldCount]
   * to avoid surprising behaviour like retrieving a result map which is smaller than requested by a
   * completely random factor.
   */
  fun hrandFieldWithValues(key: String, count: Long): Map<String, ByteString>?

  /**
   * Like [hrandFieldWithValues] but only returns the fields of the hash stored at [key].
   */
  fun hrandField(key: String, count: Long): List<String>

  /**
   * Sets the [ByteString] value for the given key.
   *
   * @param key the key to set
   * @param value the value to set
   */
  operator fun set(key: String, value: ByteString)

  /**
   * Sets the [ByteString] value for a key with an expiration date.
   *
   * @param key the key to set
   * @param expiryDuration the amount of time before the key expires
   * @param value the value to set
   */
  operator fun set(key: String, expiryDuration: Duration, value: ByteString)

  /**
   * Sets the [ByteString] value for the given key if it does not already exist.
   *
   * @param key the key to set
   * @param value the value to set
   */
  fun setnx(key: String, value: ByteString): Boolean

  /**
   * Sets the [ByteString] value for the given key if it does not already exist.
   *
   * @param key the key to set
   * @param expiryDuration the amount of time before the key expires
   * @param value the value to set
   */
  fun setnx(key: String, expiryDuration: Duration, value: ByteString): Boolean

  /**
   * Sets the [ByteString] value for the given key and field
   *
   * @param key the key
   * @param field the field
   * @param value the value to set
   * @return The number of fields that were added.
   *         Returns 0 if all fields had their values overwritten.
   */
  fun hset(key: String, field: String, value: ByteString): Long

  /**
   * Sets the [ByteString] values for the given key and fields
   *
   * @param key the key
   * @param hash the map of fields to [ByteString] value
   * @return The number of fields that were added.
   *         Returns 0 if all fields had their values overwritten.
   */
  fun hset(key: String, hash: Map<String, ByteString>): Long

  /**
   * Increments the number stored at key by one. If the key does not exist, it is set to 0 before
   * performing the operation. An error is returned if the key contains a value of the wrong type or
   * contains a string that can not be represented as integer.
   *
   * Note: this is a string operation because Redis does not have a dedicated integer type. The
   * string stored at the key is interpreted as a base-10 64 bit signed integer to execute the
   * operation.
   *
   * Redis stores integers in their integer representation, so for string values that actually hold
   * an integer, there is no overhead for storing the string representation of the integer.
   */
  fun incr(key: String): Long

  /**
   * Increments the number stored at key by increment. If the key does not exist, it is set to 0
   * before performing the operation. An error is returned if the key contains a value of the wrong
   * type or contains a string that can not be represented as integer.
   *
   * See [incr] for extra information.
   */
  fun incrBy(key: String, increment: Long): Long

  /**
   * [blmove] is the blocking variant of [lmove]. When source contains elements, this command
   * behaves exactly like [lmove]. When used inside a MULTI/EXEC block, this command behaves exactly
   * like [lmove]. When source is empty, Redis will block the connection until another client pushes
   * to it or until timeout (a double value specifying the maximum number of seconds to block) is
   * reached. A timeout of zero can be used to block indefinitely.
   *
   * This command comes in place of the now deprecated [brpoplpush]. Doing BLMOVE RIGHT LEFT is
   * equivalent.
   *
   * See [lmove] for more information.
   */
  fun blmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection,
    timeoutSeconds: Double
  ): ByteString?

  /**
   * [brpoplpush] is the blocking variant of [rpoplpush]. When source contains elements, this
   * command behaves exactly like [rpoplpush]. When used inside a MULTI/EXEC block, this command
   * behaves exactly like [rpoplpush]. When source is empty, Redis will block the connection until
   * another client pushes to it or until timeout is reached. A timeout of zero can be used to block
   * indefinitely.
   *
   * See [rpoplpush] for more information.
   *
   * As of Redis version 6.2.0, this command is regarded as deprecated.
   *
   * It can be replaced by [blmove] with the RIGHT and LEFT arguments when migrating or writing new
   * code.
   */
  fun brpoplpush(sourceKey: String, destinationKey: String, timeoutSeconds: Int): ByteString?

  /**
   * Atomically returns and removes the first/last element (head/tail depending on the wherefrom
   * argument) of the list stored at source, and pushes the element at the first/last element
   * (head/tail depending on the whereto argument) of the list stored at destination.
   *
   * For example: consider source holding the list a,b,c, and destination holding the list x,y,z.
   * Executing LMOVE source destination RIGHT LEFT results in source holding a,b and destination
   * holding c,x,y,z.
   *
   * If source does not exist, the value nil is returned and no operation is performed. If source
   * and destination are the same, the operation is equivalent to removing the first/last element
   * from the list and pushing it as first/last element of the list, so it can be considered as a
   * list rotation command (or a no-op if wherefrom is the same as whereto).
   *
   * This command comes in place of the now deprecated RPOPLPUSH. Doing LMOVE RIGHT LEFT is
   * equivalent.
   */
  fun lmove(
    sourceKey: String,
    destinationKey: String,
    from: ListDirection,
    to: ListDirection
  ): ByteString?

  /**
   * Insert all the specified values at the head of the list stored at key. If key does not exist,
   * it is created as empty list before performing the push operations. When key holds a value that
   * is not a list, an error is returned.
   *
   * It is possible to push multiple elements using a single command call just specifying multiple
   * arguments at the end of the command. Elements are inserted one after the other to the head of
   * the list, from the leftmost element to the rightmost element. So for instance the command LPUSH
   * mylist a b c will result into a list containing c as first element, b as second element and a
   * as third element.
   */
  fun lpush(key: String, vararg elements: ByteString): Long

  /**
   * Returns the specified elements of the list stored at key. The offsets start and stop are
   * zero-based indexes, with 0 being the first element of the list (the head of the list), 1 being
   * the next element and so on.
   *
   * These offsets can also be negative numbers indicating offsets starting at the end of the list.
   * For example, -1 is the last element of the list, -2 the penultimate, and so on.
   */
  fun lrange(key: String, start: Long, stop: Long): List<ByteString?>

  /**
   * Removes the first count occurrences of elements equal to element from the list stored at key.
   * The count argument influences the operation in the following ways:
   *  count > 0: Remove elements equal to element moving from head to tail.
   *  count < 0: Remove elements equal to element moving from tail to head.
   *  count = 0: Remove all elements equal to element.
   * For example, LREM list -2 "hello" will remove the last two occurrences of "hello" in the list
   * stored at list.
   *
   * Note that non-existing keys are treated like empty lists, so when key does not exist, the
   * command will always return 0.
   */
  fun lrem(key: String, count: Long, element: ByteString): Long

  /**
   * Atomically returns and removes the last element (tail) of the list stored at source, and pushes
   * the element at the first element (head) of the list stored at destination.
   *
   * For example: consider source holding the list a,b,c, and destination holding the list x,y,z.
   * Executing [rpoplpush] results in source holding a,b and destination holding c,x,y,z.
   *
   * If source does not exist, the value nil is returned and no operation is performed. If source
   * and destination are the same, the operation is equivalent to removing the last element from the
   * list and pushing it as first element of the list, so it can be considered as a list rotation
   * command.
   *
   * As of Redis version 6.2.0, this command is regarded as deprecated.
   *
   * It can be replaced by [lmove] with the RIGHT and LEFT arguments when migrating or writing new
   * code.
   */
  fun rpoplpush(sourceKey: String, destinationKey: String): ByteString?

  /**
   * Set a timeout on key. After the timeout has expired, the key will automatically be deleted. A
   * key with an associated timeout is often said to be volatile in Redis terminology.
   *
   * The timeout will only be cleared by commands that delete or overwrite the contents of the key,
   * including [del], [set], GETSET and all the *STORE commands. This means that all the operations
   * that conceptually alter the value stored at the key without replacing it with a new one will
   * leave the timeout untouched. For instance, incrementing the value of a key with [incr], pushing
   * a new value into a list with LPUSH, or altering the field value of a hash with [hset] are all
   * operations that will leave the timeout untouched.
   *
   * The timeout can also be cleared, turning the key back into a persistent key, using the PERSIST
   * command.
   *
   * If a key is renamed with RENAME, the associated time to live is transferred to the new key
   * name.
   *
   * If a key is overwritten by RENAME, like in the case of an existing key Key_A that is
   * overwritten by a call like RENAME Key_B Key_A, it does not matter if the original Key_A had a
   * timeout associated or not, the new key Key_A will inherit all the characteristics of Key_B.
   *
   * Note that calling [expire]/[pExpire] with a non-positive timeout or [expireAt]/[pExpireAt] with
   * a time in the past will result in the key being deleted rather than expired (accordingly, the
   * emitted key event will be del, not expired).
   *
   * @return true if the timeout was set. false if the timeout was not set. e.g. key doesn't exist,
   * or operation skipped due to the provided arguments.
   */
  fun expire(key: String, seconds: Long): Boolean

  /**
   * [expireAt] has the same effect and semantic as [expire], but instead of specifying the number
   * of seconds representing the TTL (time to live), it takes an absolute Unix timestamp (seconds
   * since January 1, 1970). A timestamp in the past will delete the key immediately.
   *
   * Please for the specific semantics of the command refer to the documentation of [expire].
   *
   * @return true if the timeout was set. false if the timeout was not set. e.g. key doesn't exist,
   * or operation skipped due to the provided arguments.
   */
  fun expireAt(key: String, timestampSeconds: Long): Boolean

  /**
   * This command works exactly like [expire] but the time to live of the key is specified in
   * milliseconds instead of seconds.
   *
   * @return true if the timeout was set. false if the timeout was not set. e.g. key doesn't exist,
   * or operation skipped due to the provided arguments.
   */
  fun pExpire(key: String, milliseconds: Long): Boolean

  /**
   * [pExpireAt] has the same effect and semantic as [expireAt], but the Unix time at which the key
   * will expire is specified in milliseconds instead of seconds.
   *
   * @return true if the timeout was set. false if the timeout was not set. e.g. key doesn't exist,
   * or operation skipped due to the provided arguments.
   */
  fun pExpireAt(key: String, timestampMilliseconds: Long): Boolean

  /**
   * Marks the given keys to be watched for conditional execution of a transaction.
   */
  fun watch(vararg keys: String)

  /**
   * Flushes all the previously watched keys for a transaction.
   * If you call EXEC or DISCARD, there's no need to manually call UNWATCH.
   */
  fun unwatch(vararg keys: String)

  /**
   * Marks the start of a transaction block. Subsequent commands will be queued for atomic execution
   * using EXEC.
   */
  fun multi(): Transaction

  /**
   * Begin a pipeline operation to batch together several updates for optimal performance
   */
  fun pipelined(): Pipeline
}

/**
 * Validates [count] is positive and non-zero.
 * This is to avoid unexpected behaviour due to limitations in Jedis:
 * https://github.com/redis/jedis/issues/3017
 *
 * This check can be removed when Jedis v5.x is released with full support for the behaviours
 * for negative counts that are specified by Redis.
 *
 * https://redis.io/commands/hrandfield/#specification-of-the-behavior-when-count-is-passed
 */
internal inline fun checkHrandFieldCount(count: Long) {
  require(count > -1) {
    "This Redis client does not support negative field counts for HRANDFIELD."
  }
  require(count > 0) {
    "You must request at least 1 field."
  }
}
