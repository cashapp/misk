package misk.cloud.gcp.datastore

import com.google.cloud.datastore.Datastore
import com.google.cloud.datastore.testing.LocalDatastoreHelper
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Provides
import jakarta.inject.Singleton
import misk.ServiceModule
import misk.inject.KAbstractModule
import jakarta.inject.Inject
import misk.testing.TestFixture

/** Installs a version of the [Datastore] that works off an in-memory local store */
class FakeDatastoreModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<FakeDatastoreService>())
    multibind<TestFixture>().to<FakeDatastoreService>()
  }

  @Provides
  @Singleton
  fun provideDatastoreHelper(): LocalDatastoreHelper = datastoreHelper.value

  @Provides
  @Singleton
  fun provideDatastore(datastoreHelper: LocalDatastoreHelper): Datastore =
    datastoreHelper.options.service

  @Singleton
  class FakeDatastoreService @Inject constructor(
    private val datastoreHelper: LocalDatastoreHelper
  ) : AbstractIdleService(), TestFixture {
    override fun startUp() {
      // Reset on every restart / test run
      datastoreHelper.reset()
    }

    override fun shutDown() {}

    override fun reset() {
      datastoreHelper.reset()
    }
  }

  // NB(mmihic): We use a VM-wide singleton for the datastore because starting the datastore
  // service is expensive, and we don't want to do it for every test.
  companion object {
    private val datastoreHelper = lazy {
      // Start as soon as we create it
      val helper = LocalDatastoreHelper.create(1.0)
      helper.start()
      helper
    }
  }
}
