package misk.time

/**
 * Wait until the duration has elapsed. Unlike [java.lang.Object.wait] this interprets 0 as
 * "don't wait" instead of "wait forever".
 */
fun Any.waitNanosIgnoreNotifies(nanos: Long) {
  check(nanos >= 0)
  var now = System.nanoTime()
  val waitUntil = now + nanos
  while (now < waitUntil) {
    waitNanos(waitUntil - now)
    now = System.nanoTime()
  }
}

/**
 * Wait until either a duration is elapsed or this is notified. Unlike [java.lang.Object.wait] this
 * interprets 0 as "don't wait" instead of "wait forever".
 */
@Throws(InterruptedException::class)
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun Any.waitNanos(nanos: Long) {
  val ms = nanos / 1_000_000L
  val ns = nanos - (ms * 1_000_000L)
  if (ms > 0L || ns > 0) {
    (this as Object).wait(ms, ns.toInt())
  }
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NOTHING_TO_INLINE")
inline fun Any.notify() = (this as Object).notify()

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NOTHING_TO_INLINE")
inline fun Any.notifyAll() = (this as Object).notifyAll()
