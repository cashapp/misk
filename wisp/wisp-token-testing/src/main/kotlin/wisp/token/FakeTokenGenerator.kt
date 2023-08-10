package wisp.token

import wisp.token.TokenGenerator.Companion.CANONICALIZE_LENGTH_MAX
import wisp.token.TokenGenerator.Companion.CANONICALIZE_LENGTH_MIN
import wisp.token.TokenGenerator.Companion.canonicalize
import java.util.*
import java.util.concurrent.atomic.AtomicLong

class FakeTokenGenerator : TokenGenerator {
    internal val nextByLabel = Collections.synchronizedMap<String, AtomicLong>(mutableMapOf())

    override fun generate(label: String?, length: Int): String {
        require(length in CANONICALIZE_LENGTH_MIN..CANONICALIZE_LENGTH_MAX) {
            "unexpected length: $length"
        }

        // Strip 'u' characters which aren't used in Crockford Base32 (due to possible profanity).
        val effectiveLabel = (label ?: "").replace("u", "", ignoreCase = true)

        val atomicLong = nextByLabel.computeIfAbsent(effectiveLabel) { AtomicLong(1L) }
        val suffix = atomicLong.getAndIncrement().toString()

        val unpaddedLength = effectiveLabel.length + suffix.length
        val rawResult = when {
            unpaddedLength < length -> effectiveLabel + "0".repeat(length - unpaddedLength) + suffix
            suffix.length <= length -> effectiveLabel.substring(0, length - suffix.length) + suffix
            else -> suffix.substring(suffix.length - length) // Possible collision.
        }

        return canonicalize(rawResult)
    }
}
