package misk.tokens

import misk.inject.KAbstractModule

internal class TokenGeneratorModule : KAbstractModule() {
  override fun configure() {
    bind<TokenGenerator>().to<RealTokenGenerator>()
  }
}
