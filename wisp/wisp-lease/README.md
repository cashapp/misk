# wisp-lease

This module contains the interfaces for leases. A lease can be used to fence code to ensure it is run only in the
application holding the lease.

Also in this module is a lease pool implementation.  Only one lease from a lease pool can be acquired on
each application instance.  It delegates as required to a real implementation for the lease operations.

TODO: Link to real implementation of wisp-lease...

See [wisp-lease-testing](https://github.com/cashapp/wisp/tree/main/wisp-lease-testing) for a Fake lease implementation
for use in tests.

## Usage

### General Lease Usage

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

### Using Lease Pools

```kotlin
val deployment: Deployment = Deployment.getDeploymentFromEnvironmentVariable()
val leaseManager: LeaseManager = SomeLeaseManagerImplementation()

val poolLeaseConfig =  PoolLeaseConfig(POOL_NAME, listOf(LEASE_NAME, ANOTHER_LEASE_NAME))
val poolLeaseManager = PoolLeaseManager(leaseManager, deployment, listOf(poolLeaseConfig))

// use as normal - only LEASE_NAME or ANOTHER_LEASE_NAME can ever be acquired, not both at the same time

```