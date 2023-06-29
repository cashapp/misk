package `slack-api`

import misk.inject.KAbstractModule

class FakeSlackClientModule : KAbstractModule() {
  override fun configure() {
    bind<SlackApi>().to<FakeSlackApi>()
    bind<SlackClient>().to<FakeSlackClient>()
  }
}
