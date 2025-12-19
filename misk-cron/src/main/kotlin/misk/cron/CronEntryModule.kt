package misk.cron

import kotlin.reflect.KClass
import misk.inject.KAbstractModule

internal class CronRunnableEntry
@JvmOverloads
constructor(val runnableClass: KClass<out Runnable>, val cronPattern: CronPattern? = null)

class CronEntryModule<A : Runnable>
private constructor(private val runnableClass: KClass<A>, private val cronPattern: CronPattern? = null) :
  KAbstractModule() {
  override fun configure() {
    multibind<CronRunnableEntry>().toInstance(CronRunnableEntry(runnableClass, cronPattern))
  }

  companion object {
    inline fun <reified A : Runnable> create(cronPattern: CronPattern? = null): CronEntryModule<A> =
      create(A::class, cronPattern)

    /**
     * Registers a cron runnable.
     *
     * @param cronRunnableClass: The cron runnable to register.
     */
    fun <A : Runnable> create(cronRunnableClass: KClass<A>, cronPattern: CronPattern? = null): CronEntryModule<A> {
      return CronEntryModule(cronRunnableClass, cronPattern)
    }
  }
}

/**
 * Annotation to specify the cron pattern for a class.
 *
 * Uses Unix cron syntax with 5 fields: minute hour day month weekday
 *
 * Field ranges:
 * - minute: 0-59
 * - hour: 0-23 (24-hour format)
 * - day: 1-31
 * - month: 1-12
 * - weekday: 0-7 (0 and 7 are Sunday)
 *
 * Special characters:
 * - * (asterisk): matches any value
 * - , (comma): separates multiple values
 * - - (hyphen): specifies ranges
 * - / (slash): specifies step values
 *
 * Examples:
 * - "0 0 * * *" - Daily at midnight
 * - "0 *\/6 * * *" - Every 6 hours
 * - "30 9 * * 1-5" - 9:30 AM on weekdays
 * - "0 0 1 * *" - First day of every month at midnight
 * - "*\/15 * * * *" - Every 15 minutes
 * - "0 2 * * 0" - Every Sunday at 2:00 AM
 * - "45 23 * * 6" - Every Saturday at 11:45 PM
 * - "0 0,12 * * *" - Daily at midnight and noon
 * - "0 9-17 * * 1-5" - Every hour from 9 AM to 5 PM on weekdays
 */
@Target(AnnotationTarget.CLASS) annotation class CronPattern(val pattern: String)
