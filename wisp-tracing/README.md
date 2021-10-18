# wisp-tracing

Convenience functions on top of [opentracing Java APIs](https://opentracing.io/guides/java/).

## Usage

Use this library to easily set up traces...

```kotlin
import wisp.tracing.scoped

private val span = tracer.buildSpan("span-name").start()
tracers.scoped(span) { scope ->
  // Scope is automatically closed at end of block.
  doSomething()
}
// Span is now finished.
```

...And to more easily manipulate spans.

```kotlin
import wisp.tracing.setBaggageItems

private val span = tracer.buildSpan("span-name").start()
span.setBaggageItems(mapOf(
  "string-baggage" to "foo",
  "int-baggage" to 9999,
  "json-baggage" to moshi.adapter(Dinosaur::class.java).toJson(trex)
))
```
