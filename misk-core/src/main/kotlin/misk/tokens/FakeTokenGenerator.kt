package misk.tokens

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.testing.FakeFixture

@Singleton
class FakeTokenGenerator @Inject constructor() : FakeFixture(), TokenGenerator by wisp.token.FakeTokenGenerator()

@Singleton
class FakeTokenGenerator2 @Inject constructor() : TokenGenerator2 {
  private val tokenGenerator = wisp.token.FakeTokenGenerator()

  override fun generate(label: String?, length: Int): String {
    return tokenGenerator.generate(label, length)
  }
}
