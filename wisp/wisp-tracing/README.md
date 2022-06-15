# wisp-tracing

Convenience functions on top of [opentracing Java APIs](https://opentracing.io/guides/java/).

## Usage

Use this library to easily set up and manipulate traces.

```kotlin
import wisp.tracing.spanned

tracer.spanned("span-name") {
  // A scope and span are available in this block.
  doSomething()
  // They are automatically closed and finished at the end.
}
```

## Best practises

### Use a new scope when you change threads.

Scopes are not thread-safe, so you need to set up a new scope before switching threads.

```kotlin
import wisp.tracing.scoped
import wisp.tracing.spanned

tracer.spanned("multiple-threads") {
  thread {
   // scoped() gives you a new Scope with the same span, and by default does not finish the span.
   tracer.scoped(span) {
     doSomething()
   } 
  }
}
```

### Finish your spans, and do not re-use finished spans

The `tracer.spanned` API does not allow you use a span outside its block. This is the primary API to use.

If you need a new span, you almost always want to finish it at the end of your block:

```kotlin
import wisp.tracing.scoped
import wisp.tracing.spanned

tracer.spanned("multiple-spans") {
   tracer.scoped(tracer.buildSpan("new-span").start(), finishSpan = true) {
     doSomething()
   } 
}
```

### Use child spans

Easily create child spans.

```kotlin
import wisp.tracing.childSpan
import wisp.tracing.scoped
import wisp.tracing.spanned

tracer.spanned("parent-span") {
  // Create a new child span and finish it as soon as block finishes.
  tracer.scoped(tracer.childSpan("child-span", span), finishSpan = true) {
    doSomething()
  }
}
```

### Add baggage and tags

Add all your tags at once, instead of processing and adding them one tag at a time.

```kotlin
import wisp.tracing.spanned
import wisp.tracing.setTags
import wisp.tracing.Tag

tracer.spanned("tags-example") {
  span.setTags(listOf(
    Tag("string-tag", "string-value"),
    Tag("int-tag", 9999),
    Tag("bool-tag", true)
  ))
}
```

Add all your baggage at once, instead of processing and adding it one piece at a time. This information will be
available in downstream traces.

```kotlin
import wisp.tracing.spanned
import wisp.tracing.setBaggageItems

tracer.spanned("baggage-example") {
  span.setBaggageItems(
    mapOf(
      "string-baggage" to "foo",
      "int-baggage" to 9999,
      "json-baggage" to moshi.adapter(Dinosaur::class.java).toJson(trex)
    )
  )
  
  doSomething()
}
```
