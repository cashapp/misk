package wisp.sampling

/**
 * Abstraction for `System.nanoTime()` that allows for testing.
 */
internal interface Ticker {
    fun read(): Long

    companion object {
        val DEFAULT: Ticker = object : Ticker {
            override fun read(): Long {
                return System.nanoTime()
            }
        }
    }
}
