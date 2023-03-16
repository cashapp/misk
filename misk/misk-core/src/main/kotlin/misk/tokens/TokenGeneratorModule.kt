package misk.tokens

import misk.inject.KAbstractModule

class TokenGeneratorModule : KAbstractModule() {
  override fun configure() {
    bind<TokenGenerator>().to<RealTokenGenerator>()
    // TODO: Remove. Only here to allow breaking cycles.
    bind<wisp.token.TokenGenerator>().toInstance(wisp.token.RealTokenGenerator())
  }
}
