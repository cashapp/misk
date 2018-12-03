package misk.redis

/** A Redis client. */
interface Redis {
  /** Deletes a single key. */
  fun del(key: String): Long

  /** Deletes a number of keys. */
  fun del(vararg keys: String): Long

  /** Retrieves a key. */
  fun get(key: String): String?

  /** Sets a key. */
  fun set(key: String, value: String): String

  /** Sets a key with an expiration date. */
  fun setex(key: String, seconds: Int, value: String): String
}
