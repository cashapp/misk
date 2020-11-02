package misk.tokens

import misk.tokens.TokenGenerator.Companion.indexToChar
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.experimental.and

@Singleton
internal class RealTokenGenerator @Inject constructor() : TokenGenerator {
  private val random = SecureRandom()

  override fun generate(label: String?, length: Int): String {
    require(length in 4..25) { "unexpected length: $length" }

    val byteArray = ByteArray(length)
    random.nextBytes(byteArray)

    val result = CharArray(length)
    for (i in 0 until length) {
      result[i] = indexToChar[(byteArray[i] and 31).toInt()]
    }

    return String(result)
  }
}
