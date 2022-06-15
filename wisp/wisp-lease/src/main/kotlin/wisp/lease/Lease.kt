package wisp.lease

/**
 * A [Lease] is a cluster-wide time-based lock on a given resource. Leases are retrieved via
 * [LeaseManager.requestLease].
 *
 * It should be assumed that calls to [checkHeld], [acquire] and [release] could invoke remote
 * calls, so consider usage carefully.
 */
interface Lease {
    /** @property String the name of the resource being leased */
    val name: String

    /**
     * @return true if the lease is owned by this process instance.
     */
    fun checkHeld(): Boolean

    /**
     * Attempts to acquire the lock on the lease.  If the lock was not already held and the lock
     * was successfully obtained, listeners should be notified.
     *
     * @return true if this process acquires the lease.
     */
    fun acquire(): Boolean

    /**
     * Release the lock on the lease.  This will return true if released.  Note that it will return
     * false if the lease was not held.  Listeners should be notified before the lock is released.
     */
    fun release(): Boolean

    /**
     * Registers a listener that is called on lease state changes.
     */
    fun addListener(listener: StateChangeListener)

    interface StateChangeListener {
        /**
         * Called immediately after the lease is acquired. Also called immediately if the lease is
         * already owned by this process instance when the listener is registered.
         * @param lease the lease that is acquired
         */
        fun afterAcquire(lease: Lease)

        /**
         * Called immediately before the lease is released.
         * @param lease the lease that is released
         */
        fun beforeRelease(lease: Lease)
    }
}
