package misk.tokens

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class FakeTokenGenerator @Inject constructor() : TokenGenerator by wisp.token.FakeTokenGenerator()

@Singleton
class FakeTokenGenerator2 @Inject constructor() : TokenGenerator2 {
  private val tokenGenerator = wisp.token.FakeTokenGenerator()

  override fun generate(label: String?, length: Int): String {
    return tokenGenerator.generate(label, length)
  }
}
