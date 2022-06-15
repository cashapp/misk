package wisp.lease

class FakeLease(
    override val name: String,
    private val manager: FakeLeaseManager
) : Lease {
    private val listeners = mutableListOf<Lease.StateChangeListener>()

    override fun checkHeld() = manager.isLeaseHeld(name)

    /**
     * @return true if this process acquires the lease.
     */
    override fun acquire(): Boolean {
        val result = checkHeld()
        if (checkHeld()) {
            notifyAfterAcquire()
        }
        return result
    }

    /**
     * Release the lease.  This will return true if released.  Note that it will return false
     * if the lease was not held.
     */
    override fun release(): Boolean {
        if (!checkHeld()) {
            return false
        }
        notifyBeforeRelease()
        return true
    }

    override fun addListener(listener: Lease.StateChangeListener) {
        listeners.add(listener)
        if (checkHeld()) {
            listener.afterAcquire(this)
        }
    }

    fun notifyAfterAcquire() {
        listeners.forEach {
            it.afterAcquire(this)
        }
    }

    fun notifyBeforeRelease() {
        listeners.forEach {
            it.beforeRelease(this)
        }
    }
}
