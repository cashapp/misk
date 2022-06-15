# wisp-lease-testing

Provides
a [FakeLeaseManager](https://github.com/cashapp/wisp/blob/master/wisp-lease-testing/src/main/kotlin/wisp/lease/FakeLeaseManager.kt)
as an implement of
the [LeaseManager](https://github.com/cashapp/wisp/blob/master/wisp-lease/src/main/kotlin/wisp/lease/LeaseManager.kt)
for testing.

## Usage

```kotlin
val fakeLeaseManager = FakeLeaseManager()

// use in tests instead of a real implementation of LeaseManager
val myRealAppClass = MyRealAppClass(leaseManager = fakeLeaseManager)

// requesting a lease gives a FakeLease (just use the interface in most cases)
val lease: Lease = fakeLeaseManager.requestLease("YourLeaseName")
val fakeLease: FakeLease = lease as FakeLease

// by default leases are held unless marked otherwise, so this would change nothing at this point
fakeLeaseManager.markLeaseHeld("YourLeaseName")

// to mark the lease held somewhere else
fakeLeaseManager.markLeaseHeldElsewhere("YourLeaseName")
assertThat(lease.checkHeld()).isFalse()
assertThat(lease.acquire()).isFalse()

// add a listener and test if the lease is held...
val leaseHeld = AtomicBoolean()
lease.addListener(object : Lease.StateChangeListener {
  override fun afterAcquire(lease: Lease) {
    if (lease.checkHeld()) {
      leaseHeld.set(true)
    }
  }

  override fun beforeRelease(lease: Lease) {}
})

assertThat(leaseHeld.get()).isTrue()
```
