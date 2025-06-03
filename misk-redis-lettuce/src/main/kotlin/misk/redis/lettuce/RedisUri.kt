@file:Suppress("unused")

package misk.redis.lettuce

import io.lettuce.core.RedisURI
import java.net.URI

/**
 * Kotlin DSL for creating Redis URIs with a builder pattern.
 *
 * This function provides a type-safe builder pattern for configuring [RedisURI]
 * instances, supporting all Redis connection parameters.
 *
 * Example usage:
 * ```kotlin
 * val uri = redisUri {
 *   withHost("redis.example.com")
 *   withPort(6379)
 *   withPassword("secret")
 *   withSsl(true)
 *   withTimeout(Duration.ofSeconds(5))
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [RedisURI.Builder]
 * @return Configured [RedisURI] instance
 */
inline fun redisUri(builder: RedisURI.Builder.() -> Unit): RedisURI =
  RedisURI.builder().apply(builder).build()

/**
 * Creates a Redis URI with host and optional port, supporting additional configuration.
 *
 * This function provides a convenient way to create a [RedisURI] with basic connection
 * details while still allowing for additional configuration through the builder pattern.
 *
 * Example usage:
 * ```kotlin
 * // Basic usage
 * val uri1 = redisUri("redis.example.com", 6379)
 *
 * // With additional configuration
 * val uri2 = redisUri("redis.example.com", 6379) {
 *   withPassword("secret")
 *   withSsl(true)
 * }
 * ```
 *
 * @param host The Redis server hostname
 * @param port Optional port number (defaults to standard Redis port)
 * @param builder Optional lambda for additional configuration
 * @return Configured [RedisURI] instance
 */
inline fun redisUri(
  host: String,
  port: Int? = null,
  builder: RedisURI.Builder.() -> Unit = {}
): RedisURI =
  redisUri {
    withHost(host)
    port?.let { withPort(port) }
    builder()
  }

/**
 * Creates a Redis URI from a URI string.
 *
 * This function parses a string URI into a [RedisURI], supporting both Redis
 * and RedisS (SSL) schemes.
 *
 * Example usage:
 * ```kotlin
 * val uri1 = redisUri("redis://redis.example.com:6379")
 * val uri2 = redisUri("rediss://redis.example.com:6379/0")
 * ```
 *
 * Supported formats:
 * - `redis://[[user:]password@]host[:port][/database]`
 * - `rediss://[[user:]password@]host[:port][/database]` (SSL)
 *
 * @param uri The Redis URI string
 * @return Parsed [RedisURI] instance
 * @throws IllegalArgumentException if the URI format is invalid
 */
fun redisUri(uri: String): RedisURI =
  RedisURI.create(uri)

/**
 * Creates a Redis URI from a [URI] object.
 *
 * This function converts a standard Java [URI] into a Redis-specific [RedisURI],
 * supporting both Redis and RedisS (SSL) schemes.
 *
 * Example usage:
 * ```kotlin
 * val javaUri = URI("redis://redis.example.com:6379")
 * val redisUri = redisUri(javaUri)
 * ```
 *
 * @param uri The Java URI object
 * @return Converted [RedisURI] instance
 * @throws IllegalArgumentException if the URI scheme is not supported
 */
fun redisUri(uri: URI): RedisURI =
  RedisURI.create(uri)




