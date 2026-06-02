package wisp.lease

/**
 * A [Lease] is a cluster-wide, time-based lock on a given resource. Leases are retrieved via
 * [LeaseManager.requestLease].
 *
 * It should be assumed that calls to [checkHeld], [acquire] and [release] could invoke remote calls, so consider usage
 * carefully.
 */
interface Lease {
  /** @property String the name of the resource being leased */
  val name: String

  /**
   * Check if this process should own the lease. For example, if a lease is first-come first-serve, then this process
   * should try to own the lease, whereas a lease that uses consistent hashing should try to own if and only if it is
   * assigned the lease.
   *
   * Note that it is possible for a lease to be held by a process that is not supposed to own it, e.g. a process is
   * holding the lease but a new process starts and is assigned the lease via consistent hashing.
   *
   * This method should not attempt to acquire the lease, make network calls, or block.
   *
   * @return true if this process instance should own the lease.
   */
  fun shouldHold(): Boolean

  /**
   * Check if this process owns the lease.
   *
   * This method should not attempt to acquire the lease, make network calls, or block.
   *
   * @return whether the lease is owned by this process instance.
   */
  fun isHeld(): Boolean

  /** @return true if the lease is owned by this process instance. */
  @Deprecated("prefer calling isHeld() to avoid network calls", ReplaceWith("isHeld()")) fun checkHeld(): Boolean

  /** @return true if the lease is owned by another process instance. */
  @Deprecated("lease is not guaranteed to be held elsewhere, do not depend on this") fun checkHeldElsewhere(): Boolean

  /**
   * Attempts to acquire the lock on the lease. If the lock was not already held and the lock was successfully obtained,
   * listeners should be notified.
   *
   * @return true if this process acquires the lease.
   */
  fun acquire(): Boolean

  /**
   * Release the lock on the lease. This will return true if released. Note that it will return false if the lease was
   * not held. Listeners should be notified before the lock is released.
   */
  fun release(): Boolean

  /**
   * Release the lock on the lease, with the option of doing it lazily if possible. This should return false if the
   * lease was not held or if the release is lazy. Listeners should be notified before the lock is released.
   *
   * @param lazy whether to attempt to release the lease lazily
   */
  fun release(lazy: Boolean): Boolean

  /** Registers a listener that is called on lease state changes. */
  fun addListener(listener: StateChangeListener)

  interface StateChangeListener {
    /**
     * Called immediately after the lease is acquired. Also called immediately if the lease is already owned by this
     * process instance when the listener is registered.
     *
     * @param lease the lease that is acquired
     */
    fun afterAcquire(lease: Lease)

    /**
     * Called immediately before the lease is released.
     *
     * @param lease the lease that is released
     */
    fun beforeRelease(lease: Lease)
  }
}
