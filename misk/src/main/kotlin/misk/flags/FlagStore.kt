package misk.flags

import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Applications register their interest in monitoring flags through the [registerFlag]
 * calls. If registration and watching requires contacting a remote service (e.g. a flag service,
 * Zookeeper, etc), this should be done asynchronously so that multiple flags can be registered
 * in parallel, with the [Flag.get] calls blocking until the registration for that flag is
 * complete. An application can also call [awaitRegistrationsComplete] to block until all
 * pending registrations have completed, allowing e.g. the application to avoid processing
 * incoming requests until all of its flags are available and watched
 */
interface FlagStore {
  /** Asynchronously registers interest in the given flag of the provided type */
  fun <T : Any> registerFlag(name: String, description: String, type: KClass<T>): Flag<T>

  /** Blocks until all pending registrations have completed */
  fun awaitRegistrationsComplete(timeout: Long, unit: TimeUnit)
}

inline fun <reified T : Any> FlagStore.registerFlag(name: String, description: String) =
  registerFlag(name, description, T::class)
