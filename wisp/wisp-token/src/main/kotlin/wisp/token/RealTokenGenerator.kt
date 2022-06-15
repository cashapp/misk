package wisp.token

import wisp.token.TokenGenerator.Companion.CANONICALIZE_LENGTH_MAX
import wisp.token.TokenGenerator.Companion.CANONICALIZE_LENGTH_MIN
import wisp.token.TokenGenerator.Companion.indexToChar
import java.security.SecureRandom
import kotlin.experimental.and

private const val REAL_TOKEN_GENERATOR_BIT_MASK = 31.toByte()

class RealTokenGenerator : TokenGenerator {
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
