package wisp.lease

/** Converts a [lease] into an [AutoCloseable] resource. */
class AutoCloseableLease(private val lease: Lease): Lease by lease, AutoCloseable {
  override fun close() {
    lease.release()
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
 */
fun LeaseManager.acquireOrNull(name: String): AutoCloseableLease? {
  val lease = requestLease(name)
  return if (lease.acquire()) AutoCloseableLease(lease) else null
}
