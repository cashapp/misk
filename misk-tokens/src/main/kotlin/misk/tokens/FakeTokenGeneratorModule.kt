package misk.tokens

import misk.inject.KAbstractModule
import misk.testing.TestFixture

class FakeTokenGeneratorModule : KAbstractModule() {
  override fun configure() {
    bind<TokenGenerator>().to<FakeTokenGenerator>()
    multibind<TestFixture>().to<FakeTokenGenerator>()
  }
}
