package misk.hibernate.vitess

object VitessExceptionDetector {
  private const val WAITER_POOL_EXCEPTION_STRING = "pool waiter count exceeded"

  fun isWaiterPoolExhausted(e: Throwable?): Boolean {
    var ex = e
    var i = 0
    while (ex != null && i < 100) {
      if (ex.message != null && ex.message!!.contains(WAITER_POOL_EXCEPTION_STRING)) {
        return true
      }
      ex = ex.cause
      ++i
    }
    return false
  }
}
