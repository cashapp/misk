package misk.feature.testing

import misk.config.AppNameModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule

class TestModule : KAbstractModule() {
  override fun configure() {
    install(FakeFeatureFlagsModule())
    install(MoshiTestingModule())
    install(AppNameModule("test-app"))
    install(LogCollectorModule())
  }
}