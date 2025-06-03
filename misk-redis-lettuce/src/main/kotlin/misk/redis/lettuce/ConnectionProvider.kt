package misk.redis.lettuce

import com.google.inject.TypeLiteral
import io.lettuce.core.api.AsyncCloseable
import io.lettuce.core.api.StatefulConnection
import kotlinx.coroutines.future.await
import misk.redis.lettuce.cluster.StatefulRedisClusterConnectionProviderModule
import misk.redis.lettuce.cluster.StatefulRedisClusterConnectionProvider
import misk.redis.lettuce.standalone.StatefulRedisConnectionProviderModule
import misk.redis.lettuce.standalone.StatefulRedisConnectionProvider
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

/**
 * A provider interface for managing connections to standalone Redis servers.
 *
 * This interface  provides type-safe Redis connections with support for custom key and value types. It
 * extends [AsyncCloseable] and should be closed to release resources when no longer needed. In general,
 * misk will bind this interface to an instance of [StatefulRedisConnectionProvider] or
 * [StatefulRedisClusterConnectionProvider] and will manage the lifecycle
 *
 * It offers multiple ways to acquire connections:
 * - Asynchronous (Future-based)
 * - Suspending (Coroutine-based)
 * - Blocking (Synchronous)
 *
 */
interface ConnectionProvider<K : Any, V : Any, T : StatefulConnection<K, V>> : AsyncCloseable {

  /**
   * Asynchronously acquires a connection from the provider.
   * The returned connection must be closed after use.
   *
   * @param exclusive If true, the connection will be acquired exclusively
   * @return A [CompletableFuture] that resolves to a [T]
   * @throws NoSuchElementException when no connection is immediately available
   */
  fun acquireAsync(exclusive:Boolean = false): CompletableFuture<T>

  /**
   * Suspending function to acquire a connection from the provider.
   * The connection must be closed after use.
   *
   * @param exclusive If true, the connection will be acquired exclusively
   * @return A [T] instance
   * @throws NoSuchElementException when no connection is immediately available
   */
  suspend fun acquire(exclusive:Boolean = false): T = acquireAsync(exclusive).await()

  /**
   * Synchronously acquires a connection from the provider.
   * The connection must be closed after use.
   *
   * @param exclusive If true, the connection will be acquired exclusively
   * @return A [T] instance
   * @throws NoSuchElementException when no connection is immediately available
   */
  fun acquireBlocking(exclusive:Boolean = false): T = acquireAsync(exclusive).get()

  /**
   * Replication Group that this ConnectionProvider manages connections for
   */
  val replicationGroupId: String
}

/**
 * Function to generate a [TypeLiteral] for a [ConnectionProvider] with the given key and
 * value types. This is intended to be passed to the [StatefulRedisConnectionProviderModule] or
 * [StatefulRedisClusterConnectionProviderModule] in order specify the type to bind
 */
@Suppress("UNCHECKED_CAST")
internal inline fun <reified T : ConnectionProvider<*, *, *>> connectionProviderTypeLiteral(
  keyType: KClass<*>,
  valueType: KClass<*>
): TypeLiteral<T> =
  TypeLiteral.get(object : ParameterizedType {
    override fun getRawType(): Type = T::class.java
    override fun getOwnerType(): Type? = null
    override fun getActualTypeArguments(): Array<Type> = arrayOf(
      keyType.java,
      valueType.java
    )
  }) as TypeLiteral<T>

internal val connectionProviderTypeLiteral: TypeLiteral<ConnectionProvider<*, *, *>> =
  object : TypeLiteral<ConnectionProvider<*, *, *>>() {}
