package misk.slack.webapi

import misk.inject.KAbstractModule
import misk.testing.TestFixture

/** Binds [SlackClient] to a shared fake and resets its state between Misk tests. */
class FakeSlackClientModule : KAbstractModule() {
  override fun configure() {
    bind<SlackClient>().to<FakeSlackClient>()
    multibind<TestFixture>().to<FakeSlackClient>()
  }
}
