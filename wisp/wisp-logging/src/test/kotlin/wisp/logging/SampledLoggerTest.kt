package wisp.logging

import ch.qos.logback.classic.Level
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.sampling.Sampler

class SampledLoggerTest {

    private val logCollector = WispQueuedLogCollector()

    @BeforeEach
    fun beforeEach() {
        logCollector.startUp()
    }

    @AfterEach
    fun afterEach() {
        logCollector.shutDown()
    }

    @Test
    fun sampledLogger() {
        val sampler = MaximumSamples(2)
        val logger = getLogger<LoggingTest>().sampled(sampler)

        // clear existing messages
        logCollector.takeEvents(LoggingTest::class)

        // maximum of 2 logs should be sampled
        logger.info { "sampled test 1" }
        logger.error(NullPointerException("failed!")) { "sampled test 2" }
        logger.warn { "sampled test 3" }

        val sampledEvents = logCollector.takeEvents(LoggingTest::class, Level.ALL)
        assertThat(sampledEvents).hasSize(2)
        assertThat(sampledEvents[0]).satisfies({
            assertThat(it.level).isEqualTo(Level.INFO)
            assertThat(it.message).isEqualTo("sampled test 1")
        })
        assertThat(sampledEvents[1]).satisfies({
            assertThat(it.level).isEqualTo(Level.ERROR)
            assertThat(it.message).isEqualTo("sampled test 2")
        })

        // start allowing samples again
        sampler.resetCount()

        // maximum of 2 more logs should be sampled
        logger.debug { "sampled test 4" }
        logger.warn { "sampled test 5" }
        logger.trace { "sampled test 6" }

        val newSampledEvents = logCollector.takeEvents(LoggingTest::class, Level.ALL)
        assertThat(newSampledEvents).hasSize(2)
        assertThat(newSampledEvents[0]).satisfies({
            assertThat(it.level).isEqualTo(Level.DEBUG)
            assertThat(it.message).isEqualTo("sampled test 4")
        })
        assertThat(newSampledEvents[1]).satisfies({
            assertThat(it.level).isEqualTo(Level.WARN)
            assertThat(it.message).isEqualTo("sampled test 5")
        })
    }

    private class MaximumSamples(private val maxSamples: Int): Sampler {
        private var count = 0

        override fun sample(): Boolean {
            return count++ < maxSamples
        }

        fun resetCount() {
            count = 0
        }
    }
}
