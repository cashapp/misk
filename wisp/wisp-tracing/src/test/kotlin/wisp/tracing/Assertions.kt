package wisp.tracing

import io.opentracing.Span
import io.opentracing.mock.MockSpan
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

fun assertFinished(span: Span) {
    span as? MockSpan ?: throw AssertionError("Expected MockSpan")
    assertTrue(span.finishMicros() > 0, "Expected span to be finished.")
}

fun assertNotFinished(span: Span) {
    span as? MockSpan ?: throw AssertionError("Expected MockSpan")
    assertFailsWith<AssertionError> { span.finishMicros() }
}

fun <T> assertContainsAll(iterable: Iterable<T>, iterable2: Iterable<T>) {
    for (item in iterable2) {
        assertContains(iterable, item, "Expected $iterable to contain $item")
    }
}
