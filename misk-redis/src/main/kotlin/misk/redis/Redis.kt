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
   * Retrieves the value for the given key as a [ByteString].
   *
   * @param key the key to retrieve
   * @return a [ByteString] if the key was found, null if the key was not found
   */
  operator fun get(key: String): ByteString?

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
}
