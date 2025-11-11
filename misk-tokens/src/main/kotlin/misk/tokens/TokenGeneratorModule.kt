package misk.tokens

import misk.inject.KAbstractModule

class TokenGeneratorModule : KAbstractModule() {
  override fun configure() {
    bind<TokenGenerator>().to<RealTokenGenerator>()
    bind<TokenGenerator2>().to<RealTokenGenerator2>()
  }
}
