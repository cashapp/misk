package wisp.sampling

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(
    expression = "Ticker",
    imports = ["com.google.common.base.Ticker"]
  )
)
/**
 * Abstraction for `System.nanoTime()` that allows for testing.
 */
interface Ticker {
    fun read(): Long

    companion object {
        val DEFAULT: Ticker = object : Ticker {
            override fun read(): Long {
                return System.nanoTime()
            }
        }
    }
}
