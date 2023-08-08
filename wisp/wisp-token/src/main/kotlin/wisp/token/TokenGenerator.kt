package wisp.token

import java.util.*

/**
 * Generates an secure, unguessable, alphanumeric token for use as a universally unique ID. Tokens
 * are encoded with a [Crockford Base32 alphabet][https://www.crockford.com/wrmg/base32.html]. This
 * alphabet contains a mix of lowercase characters and digits and is resistant to decoding errors;
 * for example `0`, `o`, and 'O' are equivalent.
 *
 * For strength similar to a [random UUID][java.util.UUID.randomUUID] (122 bits of entropy), most
 * callers should use the default length of 25 characters (125 bits). Using fewer characters risks
 * collision, which may be acceptable for some use-cases. There is no practical benefit to using
 * more than 25 characters.
 *
 * In production, staging, and development environments tokens are always created using
 * [SecureRandom][java.security.SecureRandom]. These are some sample production tokens:
 *
 * ```
 * 75dsma7kscyvbgz7ea1yy3qe8
 * 3zg6svk9hcpvqyhej41tdkaa0
 * gv7s8nkevt9d7aw2eb06g640e
 * a17f7h6t853kzdqpc29qa8mnw
 * ```
 *
 * In tests tokens are sequential and predictable. They are prefixed with an optional label that
 * appears in the returned string and can be used as a namespace. It is okay to hardcode expected
 * tokens in test cases! These are some sample testing tokens:
 *
 * ```
 * cst0mer000000000000000035
 * payment000000000000000034
 * cst0mer000000000000000036
 * payment000000000000000035
 * ```
 */
interface TokenGenerator {
    fun generate(label: String? = null, length: Int = 25): String

    companion object {
        internal const val alphabet = "0123456789abcdefghjkmnpqrstvwxyz"
        internal val indexToChar = alphabet.toCharArray()
        private const val CHAR_TO_INDEX_SIZE = 128
        private val charToIndex = ByteArray(CHAR_TO_INDEX_SIZE)
        const val CANONICALIZE_LENGTH_MIN = 4
        const val CANONICALIZE_LENGTH_MAX = 25

        init {
            Arrays.fill(charToIndex, (-1).toByte())
            for (i in indexToChar.indices) {
                val c = indexToChar[i]
                charToIndex[Character.toLowerCase(c).code] = i.toByte()
                charToIndex[Character.toUpperCase(c).code] = i.toByte()
                if (c == '0') {
                    charToIndex['o'.code] = i.toByte()
                    charToIndex['O'.code] = i.toByte()
                }
                if (c == '1') {
                    charToIndex['i'.code] = i.toByte()
                    charToIndex['I'.code] = i.toByte()
                    charToIndex['l'.code] = i.toByte()
                    charToIndex['L'.code] = i.toByte()
                }
            }
        }

        /**
         * Returns a token semantically equal to `token` but using only characters from the Crockford
         * Base32 alphabet. This maps visually similar characters like `o` to the corresponding
         * encoding character like `0`. Spaces are omitted.
         *
         * Call this when accepting tokens that may have been transcribed by a user. It is not necessary
         * to canonicalize tokens that haven't been manually entered; they will already be in canonical
         * form.
         *
         * @throws IllegalArgumentException if `token` contains a character that cannot be mapped and
         *     that is not a space.
         */
        fun canonicalize(token: String): String {
            val result = StringBuilder()
            for (c in token) {
                if (c == ' ') continue
                require(c.code in 0 until CHAR_TO_INDEX_SIZE) { "unexpected token $token" }
                val index = charToIndex[c.code].toInt()
                require(index != -1) { "unexpected token $token" }
                result.append(indexToChar[index])
            }
            require(result.length in CANONICALIZE_LENGTH_MIN..CANONICALIZE_LENGTH_MAX)
            return result.toString()
        }
    }
}
