package misk.tokens

import misk.inject.KAbstractModule

class FakeTokenGeneratorModule : KAbstractModule() {
  override fun configure() {
    bind<TokenGenerator>().to<FakeTokenGenerator>()
    bind<TokenGenerator2>().to<FakeTokenGenerator2>()
    bind<wisp.token.TokenGenerator>().toInstance(wisp.token.FakeTokenGenerator())
  }
}
