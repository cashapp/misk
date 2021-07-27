# wisp-lease-testing

Provides a [FakeLeaseManager](https://github.com/cashapp/misk/blob/master/wisp-lease-testing/src/main/kotlin/wisp/lease/FakeLeaseManager.kt)
as an implement of the [LeaseManager](https://github.com/cashapp/misk/blob/master/wisp-lease/src/main/kotlin/wisp/lease/LeaseManager.kt)
for testing.

## Usage

```kotlin
val fakeLeaseManager = FakeLeaseManager()

// use in tests instead of a real implementation of LeaseManager
val myRealAppClass = MyRealAppClass(leaseManager = fakeLeaseManager)

// requesting a lease gives a FakeLease (just use the interface in most cases)
val lease: Lease = fakeLeaseManager.requestLease("YourLeaseName")
val fakeLease: FakeLease = lease as FakeLease

// add a listener and test if the lease is held...
val acquireCalled = AtomicBoolean()
lease.addListener(object : Lease.StateChangeListener {
  override fun afterAcquire(lease: Lease) {
    lease.checkHeld()
    acquireCalled.set(true)
  }

  override fun beforeRelease(lease: Lease) {}
})

assertThat(acquireCalled.get()).isTrue()
```
