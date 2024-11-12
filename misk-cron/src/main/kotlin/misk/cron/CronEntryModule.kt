package misk.cron

import misk.inject.KAbstractModule
import kotlin.reflect.KClass

internal class CronRunnableEntry @JvmOverloads constructor(val runnableClass: KClass<out Runnable>, val cronPattern: CronPattern? = null)

class CronEntryModule<A : Runnable> private constructor(
  private val runnableClass: KClass<A>,
  private val cronPattern: CronPattern? = null
) : KAbstractModule() {
  override fun configure() {
    multibind<CronRunnableEntry>().toInstance(CronRunnableEntry(runnableClass, cronPattern))
  }
  companion object {
    inline fun <reified A : Runnable> create(cronPattern: CronPattern? = null): CronEntryModule<A> = create(A::class, cronPattern)

    /**
     * Registers a cron runnable.
     * @param cronRunnableClass: The cron runnable to register.
     */
    fun <A : Runnable> create(cronRunnableClass: KClass<A>, cronPattern: CronPattern? = null): CronEntryModule<A> {
      return CronEntryModule(cronRunnableClass, cronPattern)
    }
  }
}

@Target(AnnotationTarget.CLASS)
annotation class CronPattern(val pattern: String)
