package misk.tokens

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class RealTokenGenerator @Inject constructor() : TokenGenerator by wisp.token.RealTokenGenerator()

@Singleton
class RealTokenGenerator2 @Inject constructor() : TokenGenerator2 {
  private val tokenGenerator = wisp.token.RealTokenGenerator()

  override fun generate(label: String?, length: Int): String {
    return tokenGenerator.generate(label, length)
  }
}
