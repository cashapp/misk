# misk-redis-lettuce

A Misk module providing Redis connectivity for both standalone and cluster configurations. This module offers type-safe Redis operations, connection pooling, and flexible codec configuration.

## Features

- Support for both standalone Redis and Redis Cluster deployments
- Type-safe Redis operations with customizable key and value types
- Flexible codec configuration for data serialization
- Automatic connection management and pooling
- SSL/TLS support for secure connections
- Support for Redis replication with read/write splitting

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.squareup.misk:misk-redis-lettuce")
}
```

## Configuration

### Standalone Redis Configuration

Configure Redis in your YAML configuration file:

```yaml
redis:
  cash-misk-exemplar-001:  # Replication group ID
    client_name: "exemplar"
    primary_endpoint:
      hostname: "redis.example.com"
      port: 6379
    reader_endpoint:  # Optional read-only replica
      hostname: "redis-replica.example.com"
      port: 6379
    redis_auth_password: "secret"
    timeout_ms: 5000
    connectionPoolConfig:
      enabled: true
      maxTotal: 16
      maxIdle: 8
      minIdle: 2
      testOnCreate: false
      testOnAcquire: false
      testOnRelease: false
```

### Redis Cluster Configuration

For Redis Cluster deployments, use this YAML configuration:

```yaml
redis_cluster:
  cash-misk-cluster-001:  # Cluster group ID
    client_name: "exemplar"
    configuration_endpoint:
      hostname: "redis-cluster.example.com"
      port: 6379
    redis_auth_password: "secret"
    timeout_ms: 5000
    connectionPoolConfig:
      enabled: true
      maxTotal: 32
      maxIdle: 8
      minIdle: 2
```

## Usage

### Basic Usage with String Codec

Install the Redis module in your application:

```kotlin
class ExampleModule : KAbstractModule() {
  override fun configure() {
    // Configure Redis with default UTF-8 String codec
    val config = RedisConfig(
      "misk-exemplar-001" to RedisReplicationGroupConfig(
        primary_endpoint = RedisNodeConfig(
          hostname = "redis001.example.com",
          port = 6379
        ),
        reader_endpoint = RedisNodeConfig(
          hostname = "redis002.example.com",
          port = 6379
        ),
        redis_auth_password = "secret",
        timeout_ms = 2000
      )
    )

    // Create and install the module
    install(RedisModule.create(
      config = config,
    ))
  }
}
```

If pulling config from  yaml:
```kotlin
class MyServiceExampleModule(val config: RedisConfig): KAbstractModule() {
  override fun configure() {
    install(RedisModule.create(config))
  }
}
```

### Using Connection Providers

The `misk-redis-lettuce` module provides separate connection providers for read-write and read-only operations. This is particularly useful when working with Redis replication. The providers support coroutine-based, blocking, and asynchronous APIs:

```kotlin
class UserCacheService @Inject constructor(
  private val writeProvider: ReadWriteConnectionProvider,
  private val readProvider: ReadOnlyConnectionProvider
) {
  // Write operation using coroutines
  suspend fun cacheUser(id: String, userData: String) {
    writeProvider.withConnection {
      set(id, userData)
    }
  }

  // Read operation using coroutines
  suspend fun getUser(id: String): String? {
    return readProvider.withConnection {
      get(id)
    }
  }

  // Write operation using blocking API
  fun cacheUserBlocking(id: String, userData: String) {
    writeProvider.withConnectionBlocking {
      set(id, userData)
    }
  }

  // Read operation using blocking API
  fun getUserBlocking(id: String): String? {
    return readProvider.withConnectionBlocking {
      get(id)
    }
  }
  
  // Pipeline multiple operations
  suspend fun batchGetUsers(ids: List<String>): List<String?> {
    return readProvider.withConnection {
      ids.map { id -> get(id) }
    }
  }

  // Async write operation using CompletionStage
  fun cacheUserAsync(id: String, userData: String): CompletionStage<String> {
    return writeProvider.asyncAcquire()
      .thenComposeUsing { connection ->
        connection.async().set(id, userData)
      }
  }

  // Async read operation with connection auto-closing
  fun getUserAsync(id: String): CompletionStage<String?> {
    return readProvider.asyncAcquire()
      .thenComposeUsing { connection ->
        connection.async().get(id)
      }
  }

  // Multiple async operations in sequence
  fun updateUserDataAsync(id: String, newData: String): CompletionStage<Boolean> {
    return writeProvider.asyncAcquire()
      .thenComposeUsing { connection ->
        val async = connection.async()
        async.get(id)
          .thenCompose { oldData ->
            if (oldData != null) {
              async.set(id, newData)
                .thenApply { true }
            } else {
              CompletableFuture.completedFuture(false)
            }
          }
      }
  }

  // Error handling with async operations
  fun handleErrorsAsync(id: String): CompletionStage<String> {
    return writeProvider.asyncAcquire()
      .thenComposeUsing { connection ->
        connection.async().get(id)
          .thenCompose { value ->
            if (value == null) {
              CompletableFuture.failedFuture(NoSuchElementException("Key not found: $id"))
            } else {
              CompletableFuture.completedFuture(value)
            }
          }
      }
      .exceptionally { throwable ->
        // Handle the error, connection is already closed
        logger.error("Error processing key $id", throwable)
        throw throwable
      }
  }
}
```

The connection providers offer three API styles:
1. **Coroutine-based**:
   - `withConnection`: For suspending coroutine-based operations
   - Best for modern Kotlin codebases using coroutines

2. **Blocking**:
   - `withConnectionBlocking`: For synchronous blocking operations
   - Useful for simple operations or legacy code

3. **Asynchronous**:
   - `asyncAcquire()`: Acquires a connection asynchronously
   - `thenComposeUsing` or `thenApplyUsing`: Automatically manages connection lifecycle
   - Ideal for non-blocking workflows and high-throughput scenarios

Type aliases are provided for common string-based operations:
- `ReadWriteConnectionProvider` = `ReadWriteStatefulRedisConnectionProvider<String, String>`
- `ReadOnlyConnectionProvider` = `ReadOnlyStatefulRedisConnectionProvider<String, String>`
- `ClusterConnectionProvider` = `StatefulRedisClusterConnectionProvider<String, String>`

For custom key/value types, use the generic interfaces:
```kotlin
class CustomCacheService @Inject constructor(
  private val writeProvider: ReadWriteStatefulRedisConnectionProvider<UserId, UserData>,
  private val readProvider: ReadOnlyStatefulRedisConnectionProvider<UserId, UserData>
) {
  suspend fun cacheUser(id: UserId, userData: UserData) {
    writeProvider.withConnection {
      set(id, userData)
    }
  }
}
```

### Custom Types and Codec

For custom data types, implement a custom codec:

```kotlin
class JsonCodec : RedisCodec<String, JsonNode> {
  private val stringCodec = StringCodec.UTF8
  private val mapper = ObjectMapper()

  override fun decodeKey(bytes: ByteBuffer): String =
    stringCodec.decodeKey(bytes)

  override fun decodeValue(bytes: ByteBuffer): JsonNode =
    mapper.readTree(bytes.array())

  override fun encodeKey(key: String): ByteBuffer =
    stringCodec.encodeKey(key)

  override fun encodeValue(value: JsonNode): ByteBuffer =
    ByteBuffer.wrap(mapper.writeValueAsBytes(value))
}

// Install module with custom types
class CustomRedisModule : KAbstractModule() {
  override fun configure() {
    val config = RedisConfig(/* ... */)
    
    install(RedisModule.create<String, JsonNode>(
      config = config,
      codec = JsonCodec(),
    ))
  }
}
```

### Redis Cluster Setup

For Redis Cluster deployments:

```kotlin
class ClusterRedisModule : KAbstractModule() {
  override fun configure() {
    val config = RedisClusterConfig(
      "misk-cluster-001" to RedisClusterGroupConfig(
        configuration_endpoint = RedisNodeConfig(
          hostname = "redis-cluster.example.com",
          port = 6379
        ),
        redis_auth_password = "secret",
        timeout_ms = 2000,
        connectionPoolConfig = RedisConnectionPoolConfig(
          enabled = true,
          maxTotal = 32,
          maxIdle = 8,
          minIdle = 2
        )
      )
    )

    install(RedisModule.create(
      config = config,
    ))
  }
}
```

If pulling config from yaml:
```kotlin
class MyServiceExampleModule(val config: RedisClusterConfig): KAbstractModule() {
  override fun configure() {
    install(RedisModule.create(config))
  }
}
```

## Understanding Connection Pooling

Lettuce, the Redis client used by misk-redis-lettuce, is designed to be thread-safe and efficient without connection pooling. Here's what you need to know:

### When You Don't Need Connection Pooling

Connection pooling is **not** required for performance in most cases because:

1. Lettuce connections are fully thread-safe
2. A single connection can handle multiple concurrent requests
3. The client uses non-blocking I/O, allowing high throughput on a single connection
4. Connection state is managed automatically by Lettuce

### When to Consider Connection Pooling

You should consider enabling connection pooling only in these specific scenarios:

1. **Transactions**: When using Redis transactions (`MULTI`/`EXEC`) that block the connection
2. **Blocking Operations**: When using blocking commands like `BLPOP` or `BRPOP`
3. **Pub/Sub**: When subscribing to channels, as the connection is dedicated to the subscription
4. **Connection Lifecycle Management**: When you need fine-grained control over connection lifecycle
5. **Legacy Systems**: When working with systems that expect pooled connections

### Connection Pool Configuration

If you do need connection pooling, configure it appropriately:

```kotlin
RedisConnectionPoolConfig(
  enabled = true,           // Enable/disable connection pooling
  maxTotal = 8,            // Maximum total connections
  maxIdle = 8,             // Maximum idle connections
  minIdle = 1,             // Minimum idle connections
  testOnCreate = false,    // Test connections when created
  testOnAcquire = false,   // Test connections when borrowed
  testOnRelease = false    // Test connections when returned
)
```

## Default Values

- Default timeout: 10,000 ms
- Default pool maximum total connections: 8
- Default pool maximum idle connections: 8
- Default pool minimum idle connections: 1
- Default connection testing settings: all disabled

## Security
- Passwords in configuration are marked with `@misk.config.Redact` to prevent leaking in admin UI or logs
- Client names can be set for monitoring and auditing


## Best Practices

1. Evaluate if you really need connection pooling before enabling it
2. Configure appropriate timeouts based on your use case
3. Set meaningful client names for better monitoring
4. Use read replicas when available for read-heavy workloads
5. Implement custom codecs for complex data types
6. Enable SSL/TLS in production environments
7. Use async operations for non-blocking workflows
8. Properly handle errors in async operations
9. Consider using coroutines with suspending functions for simpler async code
