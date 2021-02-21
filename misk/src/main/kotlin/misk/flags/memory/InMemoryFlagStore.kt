package misk.flags.memory

import misk.flags.FlagStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/** In memory implementation of a [FlagStore], suitable for use in testing */
@Singleton
class InMemoryFlagStore @Inject constructor() : FlagStore {
  private val _flags = ConcurrentHashMap<String, InMemoryFlag<*>>()

  val flags: Map<String, InMemoryFlag<*>> get() = _flags
  val booleanFlags: Map<String, InMemoryBooleanFlag> get() = flagsOfType()
  val intFlags: Map<String, InMemoryIntFlag> get() = flagsOfType()
  val stringFlags: Map<String, InMemoryStringFlag> get() = flagsOfType()
  val doubleFlags: Map<String, InMemoryDoubleFlag> get() = flagsOfType()

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> registerFlag(
    name: String,
    description: String,
    type: KClass<T>
  ): InMemoryFlag<T> {
    return when (type) {
      Int::class -> registerFlag(InMemoryIntFlag(name, description)) as InMemoryFlag<T>
      Boolean::class -> registerFlag(InMemoryBooleanFlag(name, description)) as InMemoryFlag<T>
      Double::class -> registerFlag(InMemoryDoubleFlag(name, description)) as InMemoryFlag<T>
      String::class -> registerFlag(InMemoryStringFlag(name, description)) as InMemoryFlag<T>
      else -> throw UnsupportedOperationException("unsupported flag type ${type.java.name}")
    }
  }

  private inline fun <B, reified A : InMemoryFlag<B>> registerFlag(flag: A): A {
    val existing = _flags.putIfAbsent(flag.name, flag)
    if (existing != null) {
      return existing as? A ?: throw IllegalStateException(
        "${flag.name} already registered as ${A::class.simpleName}"
      )
    }

    return flag
  }

  private inline fun <reified A : InMemoryFlag<*>> flagsOfType(): Map<String, A> {
    return flags
      .filterValues { it is A }
      .mapValues { (_, flag) -> flag as A }
  }

  override fun awaitRegistrationsComplete(timeout: Long, unit: TimeUnit) {}
}
