package misk.cron

import misk.inject.KAbstractModule
import kotlin.reflect.KClass

internal class CronRunnableEntry(val runnableClass: KClass<out Runnable>)

class CronEntryModule<A : Runnable> private constructor(
  private val runnableClass: KClass<A>
) : KAbstractModule() {
  override fun configure() {
    multibind<CronRunnableEntry>().toInstance(CronRunnableEntry(runnableClass))
  }
  companion object {
    inline fun <reified A : Runnable> create(): CronEntryModule<A> = create(A::class)

    /**
     * Registers a cron runnable.
     * @param cronRunnableClass: The cron runnable to register.
     */
    fun <A : Runnable> create(cronRunnableClass: KClass<A>): CronEntryModule<A> {
      return CronEntryModule(cronRunnableClass)
    }
  }
}

@Target(AnnotationTarget.CLASS)
annotation class CronPattern(val pattern: String)
