package misk.tokens

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.security.SecureRandom
import kotlin.experimental.and
import misk.tokens.TokenGenerator2.Companion.CANONICALIZE_LENGTH_MAX
import misk.tokens.TokenGenerator2.Companion.CANONICALIZE_LENGTH_MIN
import misk.tokens.TokenGenerator2.Companion.indexToChar

@Singleton class RealTokenGenerator @Inject constructor() : TokenGenerator by wisp.token.RealTokenGenerator()

private const val REAL_TOKEN_GENERATOR_BIT_MASK = 31.toByte()

@Singleton
class RealTokenGenerator2 @Inject constructor() : TokenGenerator2 {
  private val random = SecureRandom()

  override fun generate(label: String?, length: Int): String {
    require(length in CANONICALIZE_LENGTH_MIN..CANONICALIZE_LENGTH_MAX) { "unexpected length: $length" }

    val byteArray = ByteArray(length)
    random.nextBytes(byteArray)

    val result = CharArray(length)
    for (i in 0 until length) {
      result[i] = indexToChar[(byteArray[i] and REAL_TOKEN_GENERATOR_BIT_MASK).toInt()]
    }

    return String(result)
  }
}
