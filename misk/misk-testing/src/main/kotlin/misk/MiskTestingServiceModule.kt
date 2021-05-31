package misk

import misk.concurrent.FakeSleeperModule
import misk.environment.FakeEnvVarModule
import misk.inject.KAbstractModule
import misk.metrics.FakeMetricsModule
import misk.random.FakeRandomModule
import misk.resources.TestingResourceLoaderModule
import misk.time.FakeClockModule
import misk.time.FakeTickerModule
import misk.tokens.FakeTokenGeneratorModule

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
    install(FakeClockModule())
    install(FakeEnvVarModule())
    install(FakeMetricsModule())
    install(FakeRandomModule())
    install(FakeSleeperModule())
    install(FakeTickerModule())
    install(FakeTokenGeneratorModule())
    install(MiskCommonServiceModule())
  }
}
