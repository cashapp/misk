package wisp.lease

class FakeLease(
  override val name: String,
  private val manager: FakeLeaseManager
) : Lease {
  private val listeners = mutableListOf<Lease.StateChangeListener>()

  override fun checkHeld() = manager.isLeaseHeld(name)

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
