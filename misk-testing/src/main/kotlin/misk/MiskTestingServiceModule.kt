package misk

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import misk.concurrent.FakeSleeperModule
import misk.environment.FakeEnvVarModule
import misk.inject.KAbstractModule
import misk.random.FakeRandomModule
import misk.resources.TestingResourceLoaderModule
import misk.time.FakeClockModule
import misk.time.FakeTickerModule
import misk.tokens.FakeTokenGeneratorModule
import org.slf4j.LoggerFactory

/**
 * [MiskTestingServiceModule] should be installed in unit testing environments.
 *
 * This should not contain application level fakes for testing. It includes a small, selective
 * set of fake bindings to replace real bindings that cannot exist in a unit testing environment
 * (e.g system env vars and filesystem dependencies).
 */
class MiskTestingServiceModule : KAbstractModule() {
  override fun configure() {
    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
//    rootLogger.level = Level.DEBUG
    install(TestingResourceLoaderModule())
    install(FakeEnvVarModule())
    install(FakeClockModule())
    install(FakeSleeperModule())
    install(FakeTickerModule())
    install(FakeRandomModule())
    install(FakeTokenGeneratorModule())
    install(MiskCommonServiceModule())
  }
}
