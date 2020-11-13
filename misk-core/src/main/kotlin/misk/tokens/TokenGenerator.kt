package misk.tokens

import java.util.Arrays

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
    private val charToIndex = ByteArray(128)

    init {
      Arrays.fill(charToIndex, (-1).toByte())
      for (i in indexToChar.indices) {
        val c = indexToChar[i]
        charToIndex[Character.toLowerCase(c).toInt()] = i.toByte()
        charToIndex[Character.toUpperCase(c).toInt()] = i.toByte()
        if (c == '0') {
          charToIndex['o'.toInt()] = i.toByte()
          charToIndex['O'.toInt()] = i.toByte()
        }
        if (c == '1') {
          charToIndex['i'.toInt()] = i.toByte()
          charToIndex['I'.toInt()] = i.toByte()
          charToIndex['l'.toInt()] = i.toByte()
          charToIndex['L'.toInt()] = i.toByte()
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
        require(c.toInt() in 0..127) { "unexpected token $token" }
        val index = charToIndex[c.toInt()].toInt()
        require(index != -1) { "unexpected token $token" }
        result.append(indexToChar[index])
      }
      require(result.length in 4..25)
      return result.toString()
    }
  }
}
