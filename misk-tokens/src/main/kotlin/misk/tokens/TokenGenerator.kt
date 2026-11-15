package misk.tokens

import java.util.Arrays

// TODO: Replace all TokenGenerator references with TokenGenerator2 then replace TokenGenerator
//  with typealias then replace all TokenGenerator2 with TokenGenerator again. Yay dependencies.

typealias TokenGenerator = wisp.token.TokenGenerator

interface TokenGenerator2 {
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
     * Returns a token semantically equal to `token` but using only characters from the Crockford Base32 alphabet. This
     * maps visually similar characters like `o` to the corresponding encoding character like `0`. Spaces are omitted.
     *
     * Call this when accepting tokens that may have been transcribed by a user. It is not necessary to canonicalize
     * tokens that haven't been manually entered; they will already be in canonical form.
     *
     * @throws IllegalArgumentException if `token` contains a character that cannot be mapped and that is not a space.
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
