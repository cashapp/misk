package misk

import misk.MiskCommonServiceModule
import misk.environment.FakeEnvVarModule
import misk.inject.KAbstractModule
import misk.resources.TestingResourceLoaderModule
import misk.time.FakeClockModule

/**
 * [MiskTestingServiceModule] should be installed in unit testing environments.
 *
 * This should not contain application level fakes for testing. It includes a small, selective
 * set of fake bindings to replace real bindings that cannot exist in a unit testing environment
 * (e.g system env vars and filesystem dependencies).
 */
class MiskTestingServiceModule : KAbstractModule() {
  override fun configure() {
    install(TestingResourceLoaderModule())
    install(FakeEnvVarModule())
    install(FakeClockModule())
    install(MiskCommonServiceModule())
  }
}
