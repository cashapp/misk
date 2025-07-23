package wisp.sampling

import java.time.Duration


@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(
    expression = "Sleeper",
    imports = ["misk.concurrent.Sleeper"]
  )
)
/**
 * Abstraction for `Thread.sleep()` that allows for testing.
 */
interface Sleeper {
    fun sleep(duration: Duration)

    companion object {
        val DEFAULT: Sleeper = object : Sleeper {
            override fun sleep(duration: Duration) {
                Thread.sleep(duration.toMillis())
            }
        }
    }
}
