# wisp-lease

This module contains the interfaces for leases.  A lease can be used to fence code to ensure
it is run only in the application holding the lease.

TODO: Link to real implementation of wisp-lease...

See [wisp-lease-testing](https://github.com/cashapp/misk/tree/master/wisp-lease-testing) for a Fake
lease implementation for use in tests.

## Usage

```kotlin
val leaseManager: LeaseManager = SomeLeaseManagerImplementation()

// request a lease
val lease = leaseManager.requestLease("MY LEASE")

// check if lease is held
if (lease.checkHeld()) {
  // got the lease...
}

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