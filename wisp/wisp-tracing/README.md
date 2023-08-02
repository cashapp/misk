# wisp-tracing

Convenience functions on top of [opentracing Java APIs](https://opentracing.io/guides/java/).

## Usage

Use this library to easily set up and manipulate traces.

```kotlin
import wisp.tracing.trace

tracer.trace("span-name") {
  // This block is instrumented with a scope and span.
  doSomething()
  // They are automatically closed and finished at the end.
}
```

## Testing

This module provides a concurrency-safe `io.opentracing.mock.MockTracer` as a testFixture.
Use it with:

```kotlin
testImplementation(testFixtures("app.cash.wisp:wisp-tracing:$version"))
```

## Best practises

### Use a new scope when you change threads.

Scopes are not thread-safe, so you need to set up a new scope before switching threads.

```kotlin
import wisp.tracing.traceWithSpan
import wisp.tracing.withNewScope

tracer.traceWithSpan("multiple-threads") { span ->
  thread { 
    // withNewScope() gives you a new Scope with the same span.
    // the scope is closed at the end of the block. 
    tracer.withNewScope(span) {
      doSomething()
    } 
  }
}
```


### Use child spans

Easily create child spans. Nested calls to trace/traceWithSpan implicitly create parent-child span relationships.

```kotlin
import wisp.tracing.trace

tracer.trace("parent-span") {
  // Create a new child span and finish it as soon as block finishes.
  tracer.trace("child-span") {
    doSomething()
  }
}
```

### Isolate interesting spans

New root spans can be created from inside parent spans. These will show up independent of the parent context.

```kotlin
import wisp.tracing.trace
import wisp.tracing.traceWithNewRootSpan

tracer.trace("universe") {
  tracer.traceWithNewRootSpan("root") {
    // Not a child.
  }
}
```

### Use baggage and tags

Add all your tags at once, instead of processing and adding them one tag at a time.
All primitive type tags are supported.

```kotlin
import wisp.tracing.traceWithSpan
import wisp.tracing.setTags
import wisp.tracing.Tag

// Use typed tags.
tracer.traceWithSpan("tags-example") {
  span.setTags(listOf(
    Tag("string-tag", "string-value"),
    Tag("int-tag", 9999),
    Tag("bool-tag", true)
  ))
}

// Or just use string tags.
tracer.trace("tags-example", tags = mapOf("a" to "b")) {
  doSomething()
}
```

Add all your baggage at once, instead of processing and adding it one piece at a time. 
This information will be available in downstream traces.
Baggage can be anything, but will always be converted to a String.

```kotlin
import wisp.tracing.setBaggageItems
import wisp.tracing.traceWithSpan

tracer.traceWithSpan("baggage-example") { span ->
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

Sometimes you may want to retain baggage from a parent context on smaller, independent traces.

```kotlin
import wisp.tracing.setBaggageItems
import wisp.tracing.traceWithSpan
import wisp.tracing.traceWithNewRootSpan

tracer.traceWithSpan("has-baggage-context") { span ->
  span.setBaggageItems(mapOf("string-baggage" to "foo"))
  tracer.traceWithNewRootSpan("new-root-span", retainBaggage = true) { newSpan ->
    assert(span.context().baggageItems().first() == newSpan.context().baggageItems().first())
  }
}
```
