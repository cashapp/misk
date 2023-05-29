package wisp.tracing

import org.junit.jupiter.api.Test
import wisp.tracing.testing.ConcurrentMockTracer
import kotlin.test.assertTrue

class TagsTest {
    private val tracer = ConcurrentMockTracer()

    @Test
    fun `Span#setTags() sets tags of different types in bulk`() {
        // No tags.
        tracer.traceWithSpan("no-tags") { span ->
            span.setTags(listOf())
        }
        var spans = tracer.finishedSpans()
        assertTrue(spans.size == 1, "Expected exactly one span")
        assertTrue(
                spans.map { it.tags() }.first().toList().isEmpty(),
                "Expected no tags"
        )
        tracer.reset()

        // With tags.
        tracer.traceWithSpan("set-tags") { span ->
            span.setTags(
                    listOf(
                            Tag("int", 9999),
                            Tag("long", Long.MAX_VALUE),
                            Tag("double", Double.MAX_VALUE),
                            Tag("float", Float.MIN_VALUE),
                            Tag("string", "string"),
                            Tag("boolean", true),
                    )
            )
        }
        spans = tracer.finishedSpans()
        assertTrue(spans.size == 1, "Expected exactly one span")

        assertContainsAll(
                spans.map { it.tags() }.first().entries,
                mapOf(
                        "int" to 9999,
                        "long" to Long.MAX_VALUE,
                        "double" to Double.MAX_VALUE,
                        "float" to Float.MIN_VALUE,
                        "string" to "string",
                        "boolean" to true
                ).entries
        )
    }
}
