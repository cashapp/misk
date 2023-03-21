package misk.tokens

import java.util.Arrays

// TODO: Replace all TokenGenerator references with TokenGenerator2 then replace TokenGenerator
//  with typealias then replace all TokenGenerator2 with TokenGenerator again. Yay dependencies.

interface TokenGenerator2 {
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
