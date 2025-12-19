package wisp.lease

class FakeLease(override val name: String, private val manager: FakeLeaseManager) : Lease {
  private val listeners = mutableListOf<Lease.StateChangeListener>()

  override fun shouldHold(): Boolean = true

  override fun isHeld(): Boolean = manager.isLeaseHeld(name)

  override fun checkHeld() = isHeld()

  /** @return true if the other process holds the lease. */
  override fun checkHeldElsewhere() = manager.isLeaseHeldElsewhere(name)

  /** @return true if this process acquires the lease. */
  override fun acquire(): Boolean {
    val result = isHeld()
    if (result) {
      notifyAfterAcquire()
    }
    return result
  }

  /** Release the lease. This will return true if released. Note that it will return false if the lease was not held. */
  override fun release(): Boolean {
    if (!isHeld()) {
      return false
    }
    notifyBeforeRelease()
    return true
  }

  override fun release(lazy: Boolean): Boolean = release()

  override fun addListener(listener: Lease.StateChangeListener) {
    listeners.add(listener)
    if (isHeld()) {
      listener.afterAcquire(this)
    }
  }

  fun notifyAfterAcquire() {
    listeners.forEach { it.afterAcquire(this) }
  }

  fun notifyBeforeRelease() {
    listeners.forEach { it.beforeRelease(this) }
  }
}
