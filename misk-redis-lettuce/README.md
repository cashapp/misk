# misk-redis-lettuce

A Misk module providing Redis connectivity for both standalone and cluster configurations. This module offers type-safe Redis operations, connection pooling, and flexible codec configuration.

## Features

- Support for both standalone Redis and Redis Cluster deployments
- Type-safe Redis operations with customizable key and value types
- Flexible codec configuration for data serialization
- Automatic connection management and pooling
- SSL/TLS support for secure connections
- Support for Redis replication with read/write splitting
- Support for Redis Functions with type-safe function loading and execution

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

### Using Command Arguments

The misk-redis2 module provides type-safe command arguments through the `CommandArguments` class. This allows you to 
specify additional options for Redis commands in an idiomatic, Kotlin way.
Example: the `setArgs` builder provides type-safe configuration for the SET command options:
```kotlin
class CacheService @Inject constructor(
  private val writeProvider: ReadWriteConnectionProvider
) {
  // Set with expiration using NX (only set if key doesn't exist)
  suspend fun setIfNotExists(key: String, value: String, expirationSeconds: Long): Boolean {
    return writeProvider.withConnection {
      // Use setArgs to specify command options
      set(key, value, setArgs {
        // Only set if key doesn't exist
        nx()
        // Set expiration in seconds
        ex(expirationSeconds)
      }) == "OK"
    }
  }

  // Set with expiration using XX (only set if key exists)
  suspend fun updateWithTtl(key: String, value: String, expirationSeconds: Long): Boolean {
    return writeProvider.withConnection {
      set(key, value, setArgs {
        // Only set if key exists
        xx()
        // Set expiration in seconds
        ex(expirationSeconds)
      }) == "OK"
    }
  }

  // Set with KEEPTTL to preserve existing TTL
  suspend fun updatePreservingTtl(key: String, value: String): Boolean {
    return writeProvider.withConnection {
      set(key, value, setArgs {
        keepttl()
      }) == "OK"
    }
  }
}
```

Similar command argument builders are available for other Redis commands. Check the `CommandArguments.kt` file for the complete list of supported arguments.

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

### Using Redis Functions with FunctionCodeLoader

The misk-redis2 module provides support for Redis Functions through the `FunctionCodeLoader` class. Redis Functions, introduced in Redis 7.0, offer several advantages over traditional EVAL scripts and MULTI/EXEC transactions:

1. **Persistence and Replication**:
   - Functions are stored as first-class database entities
   - Persist across server restarts and failovers
   - Automatically replicated to replica nodes
   - No need for client-side script management

2. **Performance Benefits**:
   - Functions are loaded once and cached server-side
   - No need to resend code with each invocation
   - Reduced network overhead compared to EVAL scripts
   - Better than MULTI/EXEC for complex operations

3. **Better Development Experience**:
   - Functions are organized into libraries for better code organization
   - Support for modular code development
   - Independent testing and deployment
   - Version control friendly

The `FunctionCodeLoader` automatically loads Redis Functions from resource files specified in your configuration. Here's how to use it:

1. **Create Function Resource Files**:
   Create your Redis Function library files in your resources directory, for example at `src/main/resources/redis/`:

   `testlib.lua`:
   ```lua
    #!lua name=testlib
   
    -- Delete a key if its value is equal to the given value
    local function del_ifeq(keys, args)
    local key = keys[1]
    local value = args[1]
        if redis.call("get", key) == value
        then
            return redis.call("del", key)
        else
            return 0
        end
    end

    redis.register_function('del_ifeq', del_ifeq)
   ```

2. **Configure Function Loading**:
   In your YAML configuration, specify the path to your function files:

   ```yaml
   redis:
     cash-misk-exemplar-001:
       client_name: "exemplar"
       primary_endpoint:
         hostname: "redis.example.com"
         port: 6379
       redis_auth_password: "secret"
       # Specify the path to your Redis Function files
       function_code_file_path: "redis/testlib.lua"
   ```

3. **Use the Functions**:
   The functions are automatically loaded when the Redis connection is established. You can then use them in your services:

   ```kotlin
   class RedisCalculatorService @Inject constructor(
     private val writeProvider: ReadWriteConnectionProvider
   ) {
     suspend fun calculateSum(keys: List<String>): Long {
       return writeProvider.withConnection { connection ->
         // Execute the pre-loaded function
         connection.fcall("del_ifeq", "balance", "0")
       }
     }
   }
   ```

4. **Function Execution Modes**:
   ```kotlin
   // Execute function with keys and arguments
   val result = connection.fcall("functionName", numKeys, *keys, *args)
   
   // Execute in read-only mode (can run on replicas)
   val result = connection.fcallReadOnly("functionName", numKeys, *keys, *args)
   ```

The `FunctionCodeLoader` handles:
- Automatic loading of functions on startup
- Reloading functions when Redis connections are re-established
- Validation of function code and error reporting
- Management of function libraries across Redis nodes

Redis Functions are preferred over EVAL scripts and MULTI/EXEC transactions when:
- You need persistent, reusable server-side logic
- The operation requires complex conditional logic or loops
- You want to ensure consistent behavior across all Redis nodes
- Performance and network efficiency are critical
- You need better code organization and maintainability

see: https://redis.io/docs/latest/develop/interact/programmability/functions-intro/

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

## Metrics

The misk-redis2 module provides metrics for monitoring Redis operations and connection pool health. These metrics are exposed via Prometheus and can be used for monitoring and alerting.

| Metric Name                                | Slug                                        | Description                                 |
|--------------------------------------------|---------------------------------------------|---------------------------------------------|
| Operation Time                             | `redis_client_operation_time_millis`        | Initial response time for Redis operations  |
| First Response Time                        | `redis_client_first_response_time_millis`   | Complete duration time for Redis operations |
| Connection Pool Max Connections            | `redis_client_max_total_connections`        | Maximum  total connections                  |
| Connection Pool Max Idle Connections       | `redis_client_max_idle_connections`         | Maximum number of idle connections          |
| Connection Pool Min Idle Connections       | `redis_client_min_idle_connections`         | Minimum number of idle connections          |
| Connection Pool Idle Connections           | `redis_client_idle_connections`             | Current number of idle connections          |
| Connection Pool Active Connection          | `redis_client_active_connections`           | Current number of active connections        |


### Common Tags

All metrics include these common tags:
- `replication_group`: The Redis replication group identifier
- `client_name`: The configured client name (if set)
- `local_address`: The ip address of the local client  
- `remote_address`: The ip address of the Redis server

Command-specific metrics also include:
- `command`: The Redis command name (e.g., "GET", "SET", "HGET")


## Security
- Passwords in configuration are marked with `@misk.config.Redact` to prevent leaking in admin UI or logs
- Client names can be set for monitoring and auditing


## Best Practices

1. Consider using coroutines with suspending functions for simpler async code
2. Use async operations for non-blocking workflows
3. Evaluate if you really need connection pooling before enabling it
4. Configure appropriate timeouts based on your use case
5. Set meaningful client names for better monitoring
6. Use read replicas when available for read-heavy workloads
7. Use Redis Functions for atomic server-side logic instead of EVAL scripts or transactions
8. Implement custom codecs for complex data types
