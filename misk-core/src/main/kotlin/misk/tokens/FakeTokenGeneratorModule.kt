package misk.tokens

import misk.inject.KAbstractModule

class FakeTokenGeneratorModule : KAbstractModule() {
  override fun configure() {
    bind<TokenGenerator>().to<FakeTokenGenerator>()
    // TODO: Remove. Only here to allow breaking cycles.
    bind<wisp.token.TokenGenerator>().toInstance(wisp.token.FakeTokenGenerator())
  }
}
