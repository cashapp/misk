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

// acquire the lease
if (lease.acquire()) {
  // got the lease....
}

// check if the lease is held (might have timed out, etc)
if (lease.checkHeld()) {
  // lease is held
}

// add a listener and test if the lease is held...
val leaseHeld = AtomicBoolean()
lease.addListener(object : Lease.StateChangeListener {
  override fun afterAcquire(lease: Lease) {
    // lease should be held at this point, but it's best to check
    if (lease.checkHeld()) {
      leaseHeld.set(true)
    }
  }

  override fun beforeRelease(lease: Lease) {}
})

assertThat(leaseHeld.get()).isTrue()

// release the lease explicitly
lease.release()
```