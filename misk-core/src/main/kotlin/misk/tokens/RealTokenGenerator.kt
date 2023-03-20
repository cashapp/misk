package misk.tokens

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealTokenGenerator @Inject constructor() : TokenGenerator {
  private val tokenGenerator = wisp.token.RealTokenGenerator()

  override fun generate(label: String?, length: Int): String {
    return tokenGenerator.generate(label, length)
  }
}

@Singleton
class RealTokenGenerator2 @Inject constructor() : TokenGenerator2 {
  private val tokenGenerator = wisp.token.RealTokenGenerator()

  override fun generate(label: String?, length: Int): String {
    return tokenGenerator.generate(label, length)
  }
}
