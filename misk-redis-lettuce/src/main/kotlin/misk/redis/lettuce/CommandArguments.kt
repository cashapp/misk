@file:Suppress("unused")

package misk.redis.lettuce

import io.lettuce.core.AclSetuserArgs
import io.lettuce.core.BitFieldArgs
import io.lettuce.core.GeoArgs
import io.lettuce.core.GetExArgs
import io.lettuce.core.LPosArgs
import io.lettuce.core.MigrateArgs
import io.lettuce.core.RestoreArgs
import io.lettuce.core.ScanArgs
import io.lettuce.core.SetArgs
import io.lettuce.core.SortArgs
import io.lettuce.core.XAddArgs
import io.lettuce.core.XClaimArgs
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.ZAddArgs
import io.lettuce.core.ZAggregateArgs
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaDuration
import kotlin.time.toJavaInstant

// Previous command arguments remain unchanged...

/**
 * Creates [MigrateArgs] for configuring Redis key migration options.
 *
 * This function provides a type-safe builder for the MIGRATE command arguments,
 * supporting various migration options between Redis instances.
 *
 * Example with basic migration:
 * ```kotlin
 * val args = migrateArgs {
 *   // Copy data instead of moving
 *   copy()
 *   // Replace existing keys
 *   replace()
 * }
 * redis.migrate("target.redis.com", 6379, "mykey", 0, 5000, args)
 * ```
 *
 * Example with multiple keys:
 * ```kotlin
 * val args = migrateArgs {
 *   copy()
 *   replace()
 *   // Migrate multiple keys
 *   keys("key1", "key2", "key3")
 *   // Set authentication
 *   auth("secret")
 * }
 * redis.migrate("target.redis.com", 6379, "", 0, 5000, args)
 * ```
 *
 * @param builder Lambda with receiver for configuring [MigrateArgs]
 * @return Configured [MigrateArgs] instance
 */
inline fun <K> migrateArgs(builder: MigrateArgs<K>.() -> Unit) = MigrateArgs<K>().apply { builder() }

/**
 * Creates [RestoreArgs] for configuring Redis key restoration options.
 *
 * This function provides a type-safe builder for the RESTORE command arguments,
 * supporting various restoration options for serialized Redis values.
 *
 * Example with basic restore:
 * ```kotlin
 * val args = restoreArgs {
 *   // Replace existing key
 *   replace()
 * }
 * redis.restore("mykey", 0, serializedValue, args)
 * ```
 *
 * Example with advanced options:
 * ```kotlin
 * val args = restoreArgs {
 *   replace()
 *   // Keep TTL from dump
 *   keepTtl()
 *   // Specify value is from IDLETIME command
 *   absttl()
 * }
 * redis.restore("mykey", ttl, serializedValue, args)
 * ```
 *
 * @param builder Lambda with receiver for configuring [RestoreArgs]
 * @return Configured [RestoreArgs] instance
 */
inline fun restoreArgs(builder: RestoreArgs.() -> Unit) = RestoreArgs().apply { builder() }


/**
 * Creates [AclSetuserArgs] for configuring Redis ACL user settings.
 *
 * This function provides a type-safe builder for the ACL SETUSER command arguments,
 * supporting comprehensive user permission configuration.
 *
 * Example with basic permissions:
 * ```kotlin
 * val args = aclSetuserArgs {
 *   // Set password
 *   addPassword("secret")
 *   // Grant permissions
 *   on()
 *   addCommands("+get", "+set")
 *   resetKeys()
 *   addKeys("user:*", "cache:*")
 * }
 * redis.aclSetuser("myuser", args)
 * ```
 *
 * Example with detailed ACL:
 * ```kotlin
 * val args = aclSetuserArgs {
 *   // Multiple passwords
 *   addPassword("secret1", "secret2")
 *
 *   // Specific permissions
 *   on()
 *   addCommands(
 *     "+get", "+set", "+del",
 *     "-flushall", "-flushdb"
 *   )
 *
 *   // Key patterns
 *   resetKeys()
 *   addKeys(
 *     "user:*",    // Allow user keys
 *     "session:*", // Allow session keys
 *     "~cache:*"   // Allow cache keys with pattern match
 *   )
 *
 *   // Channel patterns for pub/sub
 *   resetChannels()
 *   addChannels(
 *     "notifications:*",
 *     "events:*"
 *   )
 *
 *   // Set command categories
 *   addCategories(
 *     ACLPermission.READ,
 *     ACLPermission.WRITE
 *   )
 * }
 * redis.aclSetuser("myuser", args)
 * ```
 *
 * @param builder Lambda with receiver for configuring [AclSetuserArgs]
 * @return Configured [AclSetuserArgs] instance
 */
inline fun aclSetuserArgs(builder: AclSetuserArgs.() -> Unit) = AclSetuserArgs().apply { builder() }


/**
 * Creates [SetArgs] for configuring Redis SET command options.
 *
 * This function provides a type-safe builder for SET command arguments, supporting
 * expiration, existence conditions, and keep TTL options.
 *
 * Example with expiration:
 * ```kotlin
 * val args = setArgs {
 *   // Set expiration in seconds
 *   ex(60)
 *   // Only set if key doesn't exist
 *   nx()
 * }
 * redis.set("key", "value", args)
 * ```
 *
 * Example with millisecond precision:
 * ```kotlin
 * val args = setArgs {
 *   // Set expiration in milliseconds
 *   px(Duration.seconds(1.5).inWholeMilliseconds)
 *   // Only set if key exists
 *   xx()
 * }
 * redis.set("key", "value", args)
 * ```
 *
 * Example preserving TTL:
 * ```kotlin
 * val args = setArgs {
 *   // Keep existing TTL when updating value
 *   keepttl()
 * }
 * redis.set("key", "newvalue", args)
 * ```
 *
 * @param builder Lambda with receiver for configuring [SetArgs]
 * @return Configured [SetArgs] instance
 */
inline fun setArgs(builder: SetArgs.() -> Unit) = SetArgs().apply { builder() }

/**
 * Sets the expiration time in seconds using Kotlin [Duration].
 *
 * This extension function provides a Kotlin-friendly way to set expiration time
 * using Kotlin's [Duration] API instead of Java's Duration.
 *
 * Example:
 * ```kotlin
 * val args = setArgs {
 *   // Set expiration to 5 minutes using Kotlin Duration
 *   ex(5.minutes)
 * }
 * redis.set("key", "value", args)
 * ```
 *
 * @param duration The expiration time as a Kotlin [Duration]
 * @return The [SetArgs] instance for method chaining
 */
fun SetArgs.ex(duration: Duration): SetArgs = ex(duration.toJavaDuration())

/**
 * Sets the absolute expiration time in seconds using Kotlin [Instant].
 *
 * This extension function provides a Kotlin-friendly way to set absolute expiration time
 * using Kotlin's [Instant] API instead of Java's Instant.
 *
 * Example:
 * ```kotlin
 * val args = setArgs {
 *   // Set expiration to a specific point in time
 *   exAt(Clock.System.now() + 1.hours)
 * }
 * redis.set("key", "value", args)
 * ```
 *
 * @param instant The absolute expiration time as a Kotlin [Instant]
 * @return The [SetArgs] instance for method chaining
 */
fun SetArgs.exAt(instant: Instant): SetArgs = exAt(instant.toJavaInstant())

/**
 * Sets the expiration time in milliseconds using Kotlin [Duration].
 *
 * This extension function provides a Kotlin-friendly way to set expiration time
 * with millisecond precision using Kotlin's [Duration] API instead of Java's Duration.
 *
 * Example:
 * ```kotlin
 * val args = setArgs {
 *   // Set expiration to 1.5 seconds using Kotlin Duration
 *   px(1500.milliseconds)
 * }
 * redis.set("key", "value", args)
 * ```
 *
 * @param duration The expiration time as a Kotlin [Duration]
 * @return The [SetArgs] instance for method chaining
 */
fun SetArgs.px(duration: Duration): SetArgs = px(duration.toJavaDuration())

/**
 * Sets the absolute expiration time in milliseconds using Kotlin [Instant].
 *
 * This extension function provides a Kotlin-friendly way to set absolute expiration time
 * with millisecond precision using Kotlin's [Instant] API instead of Java's Instant.
 *
 * Example:
 * ```kotlin
 * val args = setArgs {
 *   // Set expiration to a specific point in time
 *   pxAt(Clock.System.now() + 90.seconds)
 * }
 * redis.set("key", "value", args)
 * ```
 *
 * @param instant The absolute expiration time as a Kotlin [Instant]
 * @return The [SetArgs] instance for method chaining
 */
fun SetArgs.pxAt(instant: Instant): SetArgs = pxAt(instant.toJavaInstant())

/**
 * Creates [GetExArgs] for configuring Redis GET with expiration command options.
 *
 * This function provides a type-safe builder for GETEX command arguments, supporting
 * various expiration options.
 *
 * Example with absolute expiration:
 * ```kotlin
 * val args = getExArgs {
 *   // Set expiration in seconds
 *   ex(60)
 * }
 * redis.getex("key", args)
 * ```
 *
 * Example with persistence:
 * ```kotlin
 * val args = getExArgs {
 *   // Remove expiration (make key persistent)
 *   persist()
 * }
 * redis.getex("key", args)
 * ```
 *
 * @param builder Lambda with receiver for configuring [GetExArgs]
 * @return Configured [GetExArgs] instance
 */
inline fun getExArgs(builder: GetExArgs.() -> Unit) = GetExArgs().apply { builder() }

/**
 * Creates [ScanArgs] for configuring Redis SCAN command options.
 *
 * This function provides a type-safe builder for SCAN command arguments, supporting
 * pattern matching and result count limits.
 *
 * Example with pattern matching:
 * ```kotlin
 * val args = scanArgs {
 *   // Match keys by pattern
 *   match("user:*")
 *   // Limit result count per iteration
 *   limit(100)
 * }
 *
 * var cursor = ScanCursor.INITIAL
 * do {
 *   val result = redis.scan(cursor, args)
 *   result.keys.forEach { key ->
 *     // Process key...
 *   }
 *   cursor = result.cursor
 * } while (!cursor.isFinished)
 * ```
 *
 * @param builder Lambda with receiver for configuring [ScanArgs]
 * @return Configured [ScanArgs] instance
 */
inline fun scanArgs(builder: ScanArgs.() -> Unit) = ScanArgs().apply { builder() }

/**
 * Creates [ZAddArgs] for configuring Redis ZADD command options.
 *
 * This function provides a type-safe builder for ZADD command arguments, supporting
 * various score update modes and condition flags.
 *
 * Example with score updates:
 * ```kotlin
 * val args = zAddArgs {
 *   // Only update existing elements
 *   xx()
 *   // Update only if new score is greater
 *   gt()
 * }
 * redis.zadd("myset", args, 1.0, "member1")
 * ```
 *
 * Example with new elements:
 * ```kotlin
 * val args = zAddArgs {
 *   // Only add new elements
 *   nx()
 *   // Return the changed elements count
 *   ch()
 * }
 * redis.zadd("myset", args, 1.0, "member1", 2.0, "member2")
 * ```
 *
 * @param builder Lambda with receiver for configuring [ZAddArgs]
 * @return Configured [ZAddArgs] instance
 */
inline fun zAddArgs(builder: ZAddArgs.() -> Unit) = ZAddArgs().apply { builder() }

/**
 * Creates [SortArgs] for configuring Redis SORT command options.
 *
 * This function provides a type-safe builder for SORT command arguments, supporting
 * sorting patterns, limits, and external key references.
 *
 * Example with basic sorting:
 * ```kotlin
 * val args = sortArgs {
 *   // Sort numerically in descending order
 *   desc()
 *   // Limit results
 *   limit(0, 10)
 * }
 * redis.sort("mylist", args)
 * ```
 *
 * Example with external keys:
 * ```kotlin
 * val args = sortArgs {
 *   // Sort by values in weight_* keys
 *   by("weight_*")
 *   // Get additional values from name_* keys
 *   get("name_*")
 *   // Sort alphabetically
 *   alpha()
 * }
 * redis.sort("users", args)
 * ```
 *
 * @param builder Lambda with receiver for configuring [SortArgs]
 * @return Configured [SortArgs] instance
 */
inline fun sortArgs(builder: SortArgs.() -> Unit) = SortArgs().apply { builder() }

/**
 * Creates [BitFieldArgs] for configuring Redis BITFIELD command options.
 *
 * This function provides a type-safe builder for BITFIELD command arguments, supporting
 * complex bit manipulation operations.
 *
 * Example with get operations:
 * ```kotlin
 * val args = bitFieldArgs {
 *   // Get 4 bits starting at offset 0 as unsigned int
 *   get(BitFieldType.unsigned(4), 0)
 *   // Get 8 bits starting at offset 8 as signed int
 *   get(BitFieldType.signed(8), 8)
 * }
 * redis.bitfield("mykey", args)
 * ```
 *
 * Example with set operations:
 * ```kotlin
 * val args = bitFieldArgs {
 *   // Set 5 bits at offset 10 with overflow wrapping
 *   overflow(BitFieldArgs.OverflowType.WRAP)
 *   set(BitFieldType.unsigned(5), 10, 12)
 *   // Increment 4 bits at offset 20
 *   incrby(BitFieldType.unsigned(4), 20, 1)
 * }
 * redis.bitfield("mykey", args)
 * ```
 *
 * @param builder Lambda with receiver for configuring [BitFieldArgs]
 * @return Configured [BitFieldArgs] instance
 */
inline fun bitFieldArgs(builder: BitFieldArgs.() -> Unit) = BitFieldArgs().apply { builder() }

/**
 * Creates [GeoArgs] for configuring Redis geospatial command options.
 *
 * This function provides a type-safe builder for geospatial command arguments,
 * supporting distance calculations and result formatting.
 *
 * Example with distance and coordinates:
 * ```kotlin
 * val args = geoArgs {
 *   // Include distance in results
 *   withDistance()
 *   // Include coordinates in results
 *   withCoordinates()
 *   // Sort by distance ascending
 *   asc()
 *   // Limit results
 *   count(10)
 * }
 * redis.geosearch("locations",
 *   GeoSearch.fromCoordinates(longitude, latitude),
 *   GeoSearch.byRadius(5, GeoArgs.Unit.km),
 *   args
 * )
 * ```
 *
 * @param builder Lambda with receiver for configuring [GeoArgs]
 * @return Configured [GeoArgs] instance
 */
inline fun geoArgs(builder: GeoArgs.() -> Unit) = GeoArgs().apply { builder() }

/**
 * Creates [XAddArgs] for configuring Redis Stream XADD command options.
 *
 * This function provides a type-safe builder for stream entry creation arguments,
 * supporting maximum length trimming and entry ID options.
 *
 * Example with trimming:
 * ```kotlin
 * val args = xAddArgs {
 *   // Trim to approximately 1000 entries
 *   maxlen(1000)
 *   // Use approximate trimming for better performance
 *   approximateTrimming()
 * }
 * redis.xadd("mystream", args, mapOf("sensor" to "1", "value" to "23.5"))
 * ```
 *
 * Example with custom ID:
 * ```kotlin
 * val args = xAddArgs {
 *   // Set specific entry ID
 *   id("1234567890-0")
 *   // Trim exactly to 100 entries
 *   exactTrimming()
 *   maxlen(100)
 * }
 * redis.xadd("mystream", args, mapOf("type" to "event", "data" to "value"))
 * ```
 *
 * @param builder Lambda with receiver for configuring [XAddArgs]
 * @return Configured [XAddArgs] instance
 */
inline fun xAddArgs(builder: XAddArgs.() -> Unit) = XAddArgs().apply { builder() }

/**
 * Creates [XReadArgs] for configuring Redis Stream XREAD command options.
 *
 * This function provides a type-safe builder for stream reading arguments,
 * supporting blocking operations and result count limits.
 *
 * Example with blocking read:
 * ```kotlin
 * val args = xReadArgs {
 *   // Block for up to 5 seconds
 *   block(5000)
 *   // Return at most 100 entries
 *   count(100)
 * }
 * redis.xread(args, XReadArgs.StreamOffset.latest("mystream"))
 * ```
 *
 * Example with multiple streams:
 * ```kotlin
 * val args = xReadArgs {
 *   count(10)
 * }
 * redis.xread(args,
 *   XReadArgs.StreamOffset.from("stream1", "0-0"),
 *   XReadArgs.StreamOffset.from("stream2", "0-0")
 * )
 * ```
 *
 * @param builder Lambda with receiver for configuring [XReadArgs]
 * @return Configured [XReadArgs] instance
 */
inline fun xReadArgs(builder: XReadArgs.() -> Unit) = XReadArgs().apply { builder() }

/**
 * Creates [XGroupCreateArgs] for configuring Redis Stream consumer group creation.
 *
 * This function provides a type-safe builder for consumer group creation arguments,
 * supporting various creation options.
 *
 * Example with basic group:
 * ```kotlin
 * val args = xGroupCreateArgs {
 *   // Create group even if stream doesn't exist
 *   mkstream()
 * }
 * redis.xgroupCreate(XGroupCreateArgs.StreamOffset.latest("mystream"), "mygroup", args)
 * ```
 *
 * Example with specific offset:
 * ```kotlin
 * val args = xGroupCreateArgs {
 *   mkstream()
 *   // Set entries per consumer
 *   entriesRead(100)
 * }
 * redis.xgroupCreate(
 *   XGroupCreateArgs.StreamOffset.from("mystream", "0-0"),
 *   "mygroup",
 *   args
 * )
 * ```
 *
 * @param builder Lambda with receiver for configuring [XGroupCreateArgs]
 * @return Configured [XGroupCreateArgs] instance
 */
inline fun xGroupCreateArgs(builder: XGroupCreateArgs.() -> Unit) = XGroupCreateArgs().apply { builder() }

/**
 * Creates [XClaimArgs] for configuring Redis Stream message claiming.
 *
 * This function provides a type-safe builder for message claiming arguments,
 * supporting various claiming options.
 *
 * Example with basic claim:
 * ```kotlin
 * val args = xClaimArgs {
 *   // Set idle time threshold
 *   idle(30000)
 *   // Return just the IDs
 *   justid()
 * }
 * redis.xclaim("mystream", "mygroup", "consumer1", 60000, "1234567890-0", args)
 * ```
 *
 * Example with advanced options:
 * ```kotlin
 * val args = xClaimArgs {
 *   // Set new idle time
 *   idle(15000)
 *   // Set retry count
 *   retrycount(3)
 *   // Force claim
 *   force()
 * }
 * redis.xclaim("mystream", "mygroup", "consumer2", 30000, "1234567890-0", args)
 * ```
 *
 * @param builder Lambda with receiver for configuring [XClaimArgs]
 * @return Configured [XClaimArgs] instance
 */
inline fun xClaimArgs(builder: XClaimArgs.() -> Unit) = XClaimArgs().apply { builder() }

/**
 * Creates [ZAggregateArgs] for configuring Redis sorted set aggregation options.
 *
 * This function provides a type-safe builder for sorted set aggregation arguments,
 * supporting various aggregation modes and weights.
 *
 * Example with weights:
 * ```kotlin
 * val args = zAggregateArgs {
 *   // Set weights for input sets
 *   weights(2.0, 1.0)
 *   // Use sum aggregation
 *   sum()
 * }
 * redis.zunion(args, "set1", "set2")
 * ```
 *
 * Example with advanced options:
 * ```kotlin
 * val args = zAggregateArgs {
 *   weights(1.0, 1.0)
 *   // Use maximum score
 *   max()
 *   // Store result in new key
 *   withScores()
 * }
 * redis.zinter(args, "set1", "set2")
 * ```
 *
 * @param builder Lambda with receiver for configuring [ZAggregateArgs]
 * @return Configured [ZAggregateArgs] instance
 */
inline fun zAggregateArgs(builder: ZAggregateArgs.() -> Unit) = ZAggregateArgs().apply { builder() }

/**
 * Creates [LPosArgs] for configuring Redis list position query options.
 *
 * This function provides a type-safe builder for list position arguments,
 * supporting rank and count options.
 *
 * Example with rank:
 * ```kotlin
 * val args = lPosArgs {
 *   // Find second occurrence
 *   rank(2)
 * }
 * redis.lpos("mylist", "value", args)
 * ```
 *
 * Example with multiple positions:
 * ```kotlin
 * val args = lPosArgs {
 *   // Find first occurrence
 *   rank(1)
 *   // Return up to 3 positions
 *   count(3)
 * }
 * redis.lpos("mylist", "value", args)
 * ```
 *
 * @param builder Lambda with receiver for configuring [LPosArgs]
 * @return Configured [LPosArgs] instance
 */
inline fun lPosArgs(builder: LPosArgs.() -> Unit) = LPosArgs().apply { builder() }
