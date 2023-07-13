package misk.slack.webapi

import misk.inject.KAbstractModule

class FakeSlackClientModule : KAbstractModule() {
  override fun configure() {
    bind<SlackApi>().to<FakeSlackApi>()
  }
}
