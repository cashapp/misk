package misk.redis

import java.time.Duration

/** A Redis client. */
interface Redis {
  /**
   * Deletes a single key.
   *
   * @param key the key to delete
   * @return 0 if the key was not deleted, 1 if the key was deleted
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
   * Retrieves a key.
   *
   * @param key the key to retrieve
   * @return a string value if the key was found, null if the key was not found
   */
  operator fun get(key: String): String?

  /**
   * Sets a value for a key.
   *
   * @param key the key to set
   * @param value the value to set
   * @return the value that was set
   */
  operator fun set(key: String, value: String): String

  /**
   * Sets a key with an expiration date.
   *
   * @param key the key to set
   * @param expiryDuration the amount of time before the key expires
   * @param value the value to set
   * @return the value that was set
   */
  fun setex(key: String, expiryDuration: Duration, value: String): String
}
