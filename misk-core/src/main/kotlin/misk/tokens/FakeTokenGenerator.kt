package misk.tokens

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.testing.FakeFixture

@Singleton
class FakeTokenGenerator @Inject constructor() : FakeFixture(), TokenGenerator {
  private val tokenGenerator = wisp.token.FakeTokenGenerator()

  override fun reset() =
    tokenGenerator.reset()

  override fun generate(label: String?, length: Int) =
    tokenGenerator.generate(label, length)
}

@Singleton
class FakeTokenGenerator2 @Inject constructor() : FakeFixture(), TokenGenerator2 {
  private val tokenGenerator = wisp.token.FakeTokenGenerator()

  override fun generate(label: String?, length: Int) =
    tokenGenerator.generate(label, length)

  override fun reset() = tokenGenerator.reset()
}
