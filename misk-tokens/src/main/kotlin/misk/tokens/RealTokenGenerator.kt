package misk.tokens

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.tokens.TokenGenerator.Companion.CANONICALIZE_LENGTH_MAX
import misk.tokens.TokenGenerator.Companion.CANONICALIZE_LENGTH_MIN
import misk.tokens.TokenGenerator.Companion.indexToChar
import java.security.SecureRandom
import kotlin.experimental.and

private const val REAL_TOKEN_GENERATOR_BIT_MASK = 31.toByte()
@Singleton
class RealTokenGenerator @Inject constructor() : TokenGenerator {
  private val random = SecureRandom()

  override fun generate(label: String?, length: Int): String {
    require(length in CANONICALIZE_LENGTH_MIN..CANONICALIZE_LENGTH_MAX) {
      "unexpected length: $length"
    }

    val byteArray = ByteArray(length)
    random.nextBytes(byteArray)

    val result = CharArray(length)
    for (i in 0 until length) {
      result[i] = indexToChar[(byteArray[i] and REAL_TOKEN_GENERATOR_BIT_MASK).toInt()]
    }

    return String(result)
  }
}
