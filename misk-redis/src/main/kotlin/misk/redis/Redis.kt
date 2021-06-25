package misk.redis

import okio.ByteString
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
}
