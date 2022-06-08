package misk.redis

import okio.ByteString
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Transaction
import java.time.Duration
import java.time.Instant

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
  fun setnx(key: String, value: ByteString)

  /**
   * Sets the [ByteString] value for the given key if it does not already exist.
   *
   * @param key the key to set
   * @param expiryDuration the amount of time before the key expires
   * @param value the value to set
   */
  fun setnx(key: String, expiryDuration: Duration, value: ByteString)

  /**
   * Sets the [ByteString] value for the given key and field
   *
   * @param key the key
   * @param field the field
   * @param value the value to set
   */
  fun hset(key: String, field: String, value: ByteString)

  /**
   * Sets the [ByteString] values for the given key and fields
   *
   * @param key the key
   * @param hash the map of fields to [ByteString] value
   */
  fun hset(key: String, hash: Map<String, ByteString>)

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
