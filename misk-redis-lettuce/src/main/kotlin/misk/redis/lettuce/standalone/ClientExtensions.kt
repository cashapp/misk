@file:Suppress("unused")

package misk.redis.lettuce.standalone

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.SocketOptions
import io.lettuce.core.SslOptions
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import kotlinx.coroutines.future.await
import misk.redis.lettuce.suspendingUse

/**
 * Creates a new [RedisClient] with the specified configuration.
 *
 * This is a convenience wrapper around [RedisClient.create] that supports optional URI and applies client options after
 * creation. It delegates to either:
 * - [RedisClient.create(ClientResources, RedisURI)] when URI is provided
 * - [RedisClient.create(ClientResources)] when URI is null
 *
 * Example with basic configuration:
 * ```kotlin
 * val client = redisClient(
 *   redisUri {
 *     withHost("redis.example.com")
 *     withPort(6379)
 *     withPassword("secret")
 *   }
 * )
 * ```
 *
 * Example with advanced configuration:
 * ```kotlin
 * val client = redisClient(
 *   redisUri {
 *     withHost("redis.example.com")
 *     withPassword("secret")
 *     withDatabase(1)
 *     withSsl(true)
 *   },
 *   clientOptions = clientOptions {
 *     // Configure timeouts
 *     timeoutOptions {
 *       timeoutCommands(true)
 *       fixedTimeout(Duration.ofSeconds(5).toJavaDuration())
 *     }
 *
 *     // Configure socket options
 *     socketOptions {
 *       connectTimeout(Duration.ofSeconds(3).toJavaDuration())
 *       keepAlive(true)
 *     }
 *
 *     // Configure SSL
 *     sslOptions {
 *       keystore(myKeyStore)
 *       truststore(myTrustStore)
 *     }
 *   },
 *   clientResources = clientResources {
 *     // Configure metrics
 *     commandLatencyRecorder(myRecorder)
 *     // Configure event bus
 *     eventBus(myEventBus)
 *   }
 * )
 * ```
 *
 * @param redisURI Optional Redis connection URI
 * @param clientResources Client resources configuration (defaults to [DefaultClientResources.create])
 * @param clientOptions Client options configuration (defaults to [ClientOptions.create])
 * @return Configured [RedisClient] instance
 */
fun redisClient(
  redisURI: RedisURI? = null,
  clientResources: ClientResources = DefaultClientResources.create(),
  clientOptions: ClientOptions = ClientOptions.create(),
): RedisClient =
  if (redisURI != null) {
      RedisClient.create(clientResources, redisURI)
    } else {
      RedisClient.create(clientResources)
    }
    .apply { options = clientOptions }

/**
 * Executes a block with a Redis connection and automatically closes it afterward.
 *
 * This suspending function creates a connection using [RedisClient.connectAsync], executes the provided block, and
 * ensures the connection is closed properly using Kotlin's [use] function. It supports custom key and value types
 * through the codec.
 *
 * Example with transaction:
 * ```kotlin
 * client.withConnection(JsonCodec()) {
 *   val sync = sync()
 *   sync.multi()  // Start transaction
 *   try {
 *     sync.set("user:1", userJson)
 *     sync.expire("user:1", 3600)
 *     sync.incr("user_count")
 *     sync.exec()  // Commit transaction
 *   } catch (e: Exception) {
 *     sync.discard()  // Rollback on error
 *     throw e
 *   }
 * }
 * ```
 *
 * Example with pipelining:
 * ```kotlin
 * client.withConnection(protobufCodec) {
 *   val async = async()
 *   // Pipeline multiple commands
 *   val results = listOf(
 *     async.set("key1", value1),
 *     async.set("key2", value2),
 *     async.mget("key1", "key2")
 *   ).map { it.get() }
 * }
 * ```
 *
 * @param K The type of keys in Redis operations
 * @param V The type of values in Redis operations
 * @param T The return type of the block
 * @param codec The codec for serializing keys and values
 * @param uri The Redis connection URI
 * @param block The code to execute with the connection
 * @return The result of the block execution
 */
suspend inline fun <K, V, T> RedisClient.withConnection(
  codec: RedisCodec<K, V>,
  uri: RedisURI,
  block: StatefulRedisConnection<K, V>.() -> T,
): T = connectAsync(codec, uri).await().suspendingUse(block)

/**
 * Executes a block with a Redis connection using UTF-8 String codec.
 *
 * This is a convenience wrapper around [withConnection] that uses [StringCodec.UTF8] for both keys and values. It
 * delegates to [RedisClient.connectAsync] with the UTF-8 String codec.
 *
 * Example with Lua script:
 * ```kotlin
 * client.withConnection(redisUri) {
 *   val script = """
 *     local current = redis.call('GET', KEYS[1])
 *     if current then
 *       redis.call('SET', KEYS[1], ARGV[1])
 *       return current
 *     end
 *     return nil
 *   """.trimIndent()
 *
 *   sync().eval(
 *     script,
 *     ScriptOutputType.VALUE,
 *     arrayOf("mykey"),
 *     "newvalue"
 *   )
 * }
 * ```
 *
 * Example with key scan:
 * ```kotlin
 * client.withConnection(redisUri) {
 *   val sync = sync()
 *   var cursor = ScanCursor.INITIAL
 *   val pattern = "user:*"
 *
 *   do {
 *     val scanResult = sync.scan(
 *       cursor,
 *       ScanArgs().match(pattern).limit(100)
 *     )
 *     scanResult.keys.forEach { key ->
 *       val value = sync.get(key)
 *       // Process key-value pair...
 *     }
 *     cursor = scanResult.cursor
 *   } while (!cursor.isFinished)
 * }
 * ```
 *
 * @param T The return type of the block
 * @param uri The Redis connection URI
 * @param block The code to execute with the connection
 * @return The result of the block execution
 */
suspend inline fun <T> RedisClient.withConnection(
  uri: RedisURI,
  block: StatefulRedisConnection<String, String>.() -> T,
): T = withConnection(StringCodec.UTF8, uri, block)

/**
 * Executes a block with a blocking Redis connection and automatically closes it.
 *
 * This function uses [RedisClient.connect] for synchronous connection creation. It supports custom key and value types
 * through the codec and handles proper connection cleanup using Kotlin's [use] function.
 *
 * Example with custom type and batch operations:
 * ```kotlin
 * data class User(val id: String, val data: ByteArray)
 *
 * client.withBlockingConnection(MyCustomCodec()) {
 *   val sync = sync()
 *
 *   // Batch get operations
 *   val users = sync.mget("user:1", "user:2", "user:3")
 *     .filterNotNull()
 *     .map { User.fromBytes(it) }
 *
 *   // Batch set operations with pipeline
 *   val async = async()
 *   users.forEach { user ->
 *     async.set("user:${user.id}", user)
 *   }
 * }
 * ```
 *
 * @param K The type of keys in Redis operations
 * @param V The type of values in Redis operations
 * @param T The return type of the block
 * @param codec The codec for serializing keys and values
 * @param uri Optional Redis connection URI
 * @param block The code to execute with the connection
 * @return The result of the block execution
 */
inline fun <K, V, T> RedisClient.withConnectionBlocking(
  codec: RedisCodec<K, V>,
  uri: RedisURI? = null,
  block: StatefulRedisConnection<K, V>.() -> T,
): T = (uri?.let { connect(codec, uri) } ?: connect(codec)).use(block)

/**
 * Executes a block with a blocking Redis connection using UTF-8 String codec.
 *
 * This is a convenience wrapper around [withConnectionBlocking] that uses [StringCodec.UTF8] for both keys and values.
 * It delegates to [RedisClient.connect] with the UTF-8 String codec.
 *
 * Example with pub/sub:
 * ```kotlin
 * client.withConnectionBlocking { conn ->
 *   // Subscribe to channels
 *   val pubsub = conn.sync()
 *   pubsub.subscribe("notifications", "alerts")
 *
 *   // Process messages
 *   while (true) {
 *     val message = pubsub.read()
 *     when (message.channel) {
 *       "notifications" -> handleNotification(message)
 *       "alerts" -> handleAlert(message)
 *     }
 *   }
 * }
 * ```
 *
 * @param T The return type of the block
 * @param uri Optional Redis connection URI
 * @param block The code to execute with the connection
 * @return The result of the block execution
 */
inline fun <T> RedisClient.withConnectionBlocking(
  uri: RedisURI? = null,
  block: StatefulRedisConnection<String, String>.() -> T,
): T = withConnectionBlocking(StringCodec.UTF8, uri, block)

/**
 * Creates [ClientResources] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [ClientResources.builder]. It allows configuration of client
 * resources using a more idiomatic builder syntax.
 *
 * Example with metrics and event bus:
 * ```kotlin
 * val resources = clientResources {
 *   // Configure command latency metrics
 *   commandLatencyRecorder(MyMetricsRecorder())
 *
 *   // Configure event bus
 *   eventBus(MyEventBus())
 *
 *   // Configure thread pools
 *   ioThreadPoolSize(4)
 *   computationThreadPoolSize(4)
 *
 *   // Configure DNS resolution
 *   dnsResolver(MyDnsResolver())
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [ClientResources.Builder]
 * @return Configured [ClientResources] instance
 */
inline fun clientResources(builder: ClientResources.Builder.() -> Unit): ClientResources =
  ClientResources.builder().apply(builder).build()

/**
 * Creates [ClientOptions] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [ClientOptions.builder]. It allows configuration of client
 * options using a more idiomatic builder syntax.
 *
 * Example with comprehensive configuration:
 * ```kotlin
 * val options = clientOptions {
 *   // Configure connection behavior
 *   disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
 *   autoReconnect(true)
 *
 *   // Configure timeouts
 *   timeoutOptions {
 *     timeoutCommands(true)
 *     fixedTimeout(Duration.ofSeconds(5).toJavaDuration())
 *   }
 *
 *   // Configure socket options
 *   socketOptions {
 *     connectTimeout(Duration.ofSeconds(3).toJavaDuration())
 *     keepAlive(true)
 *     tcpNoDelay(true)
 *   }
 *
 *   // Configure SSL
 *   sslOptions {
 *     keystore(myKeyStore)
 *     truststore(myTrustStore)
 *     protocols("TLSv1.2", "TLSv1.3")
 *   }
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [ClientOptions.Builder]
 * @return Configured [ClientOptions] instance
 */
inline fun clientOptions(builder: ClientOptions.Builder.() -> Unit): ClientOptions =
  ClientOptions.builder().apply(builder).build()

/**
 * Creates [SocketOptions] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [SocketOptions.builder]. It allows configuration of socket
 * options using a more idiomatic builder syntax.
 *
 * Example:
 * ```kotlin
 * val options = socketOptions {
 *   connectTimeout(Duration.ofSeconds(3).toJavaDuration())
 *   keepAlive(true)
 *   tcpNoDelay(true)
 *   socketReceiveBufferSize(32768)
 *   socketSendBufferSize(32768)
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [SocketOptions.Builder]
 * @return Configured [SocketOptions] instance
 */
inline fun socketOptions(builder: SocketOptions.Builder.() -> Unit): SocketOptions =
  SocketOptions.builder().apply(builder).build()

/**
 * Configures socket options for a [ClientOptions.Builder].
 *
 * This extension function provides a Kotlin-friendly way to configure socket options within a client options builder.
 * It delegates to [ClientOptions.Builder.socketOptions].
 *
 * Example:
 * ```kotlin
 * val options = clientOptions {
 *   socketOptions {
 *     connectTimeout(Duration.ofSeconds(3).toJavaDuration())
 *     keepAlive(true)
 *     tcpNoDelay(true)
 *   }
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [SocketOptions.Builder]
 * @return The [ClientOptions.Builder] for method chaining
 */
inline fun ClientOptions.Builder.socketOptions(builder: SocketOptions.Builder.() -> Unit): ClientOptions.Builder =
  this@socketOptions.socketOptions(misk.redis.lettuce.standalone.socketOptions(builder))

/**
 * Creates [SslOptions] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [SslOptions.builder]. It allows configuration of SSL options
 * using a more idiomatic builder syntax.
 *
 * Example:
 * ```kotlin
 * val options = sslOptions {
 *   keystore(myKeyStore)
 *   truststore(myTrustStore)
 *   protocols("TLSv1.2", "TLSv1.3")
 *   cipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [SslOptions.Builder]
 * @return Configured [SslOptions] instance
 */
inline fun sslOptions(builder: SslOptions.Builder.() -> Unit): SslOptions = SslOptions.builder().apply(builder).build()

/**
 * Configures SSL options for a [ClientOptions.Builder].
 *
 * This extension function provides a Kotlin-friendly way to configure SSL options within a client options builder. It
 * delegates to [ClientOptions.Builder.sslOptions].
 *
 * Example:
 * ```kotlin
 * val options = clientOptions {
 *   sslOptions {
 *     keystore(myKeyStore)
 *     truststore(myTrustStore)
 *     protocols("TLSv1.2", "TLSv1.3")
 *   }
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [SslOptions.Builder]
 * @return The [ClientOptions.Builder] for method chaining
 */
inline fun ClientOptions.Builder.sslOptions(builder: SslOptions.Builder.() -> Unit): ClientOptions.Builder =
  this@sslOptions.sslOptions(misk.redis.lettuce.standalone.sslOptions(builder))

/**
 * Creates [TimeoutOptions] using a builder pattern.
 *
 * This function provides a Kotlin-friendly wrapper around [TimeoutOptions.builder]. It allows configuration of timeout
 * options using a more idiomatic builder syntax.
 *
 * Example:
 * ```kotlin
 * val options = timeoutOptions {
 *   timeoutCommands(true)
 *   fixedTimeout(Duration.ofSeconds(5).toJavaDuration())
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [TimeoutOptions.Builder]
 * @return Configured [TimeoutOptions] instance
 */
inline fun timeoutOptions(builder: TimeoutOptions.Builder.() -> Unit): TimeoutOptions =
  TimeoutOptions.builder().apply(builder).build()

/**
 * Configures timeout options for a [ClientOptions.Builder].
 *
 * This extension function provides a Kotlin-friendly way to configure timeout options within a client options builder.
 * It delegates to [ClientOptions.Builder.timeoutOptions].
 *
 * Example:
 * ```kotlin
 * val options = clientOptions {
 *   timeoutOptions {
 *     timeoutCommands(true)
 *     fixedTimeout(Duration.ofSeconds(5).toJavaDuration())
 *   }
 * }
 * ```
 *
 * @param builder Lambda with receiver for configuring the [TimeoutOptions.Builder]
 * @return The [ClientOptions.Builder] for method chaining
 */
inline fun ClientOptions.Builder.timeoutOptions(builder: TimeoutOptions.Builder.() -> Unit): ClientOptions.Builder =
  this@timeoutOptions.timeoutOptions(misk.redis.lettuce.standalone.timeoutOptions(builder))
