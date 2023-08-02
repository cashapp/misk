package wisp.tracing

import org.junit.jupiter.api.Test
import wisp.tracing.testing.ConcurrentMockTracer
import kotlin.test.assertTrue

class BaggageTest {
    private val tracer = ConcurrentMockTracer()

    @Test
    fun `Span#setBaggageItems() can set baggage in bulk`() {
        // No baggage.
        tracer.traceWithSpan("no-baggage") { span ->
            span.setBaggageItems(mapOf())
        }
        var spans = tracer.finishedSpans()
        assertTrue(spans.size == 1, "Expected exactly one span")
        assertTrue(
                spans.map { it.context().baggageItems() }.first().toList().isEmpty(),
                "Expected no baggage"
        )

        tracer.reset()

        // With baggage.
        val baggage = mapOf(
                "movie" to "star wars",
                "release-year" to 1977,
                "producer" to Person("George Lucas")
        )
        tracer.traceWithSpan("set-baggage") { span ->
            span.setBaggageItems(baggage)
        }
        spans = tracer.finishedSpans()
        assertTrue(spans.size == 1, "Expected exactly one span")

        assertContainsAll(
                spans.map { it.context().baggageItems() }.first(),
                baggage.mapValues { (_, v) -> v.toString() }.entries
        )
    }

    private data class Person(val name: String)
}
