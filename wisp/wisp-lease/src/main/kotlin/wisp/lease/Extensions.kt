package wisp.lease

/** Converts a [lease] into an [AutoCloseable] resource. */
class AutoCloseableLease @JvmOverloads constructor(private val lease: Lease, private val lazy: Boolean = false) :
  Lease by lease, AutoCloseable {
  override fun close() {
    lease.release(lazy = lazy)
  }
}

/**
 * Attempts to acquire an [AutoCloseableLease].
 *
 * Use like
 *
 * ```
 * leaseManager.acquireOrNull("some-lease")?.use { lease ->
 *   // Do something with the lease.
 * }
 * ```
 *
 * Optionally, the lease can be released lazily (if possible)
 */
@JvmOverloads
fun LeaseManager.acquireOrNull(name: String, lazy: Boolean = false): AutoCloseableLease? {
  val lease = requestLease(name)
  return if (lease.acquire()) AutoCloseableLease(lease, lazy) else null
}
