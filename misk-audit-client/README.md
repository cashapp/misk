# misk-audit-client

This module provides an interface and fake client for sending audit events to a data warehouse.

For an example real implementation, see [samples/exemplar](../samples/exemplar) for the `ExemplarAuditClient`.

If you don't have a remote service you want to use for audit events, you can use the `NoOpAuditClient` in real environments which does not send any events to a remote service.

For a fake client to use in tests, use the `FakeAuditClient` accessible via Gradle import `testImplementation(testFixtures(libs.miskAuditClient))`.

## Waiting for HTTP audit recordings

Install `FakeAuditRequestCompletionModule` alongside `FakeAuditClientModule` to
capture the recordings from one real HTTP request, including requests without an
`@AuditRequestResponse` annotation:

```kotlin
auditClient.captureHttpRequest().use { capture ->
  webTestClient.call("/example") {
    capture.applyTo(this)
    header("Authorization", authorization)
  }.response.use { response ->
    check(response.isSuccessful)
  }
  val completedRequest = capture.awaitCompleted(Duration.ofSeconds(5))
  val recordedEvent = completedRequest.events.single()
}
```

Register before sending the request and apply each capture once. Disable automatic
retries and redirects for these test requests; a second dispatch using the same
capture is rejected. The private correlation header is removed before downstream
handlers and request logging. Authentication still uses the application's normal
request path.

Completion waits for the downstream request chain and its executed synchronous
request/response hooks, even when the response has already closed. The immutable
snapshot supports zero, one, or multiple recordings. Repeated waits and consumers
of the independent legacy `sentEvents` queue do not change it. A timeout is a
failure ceiling for that explicit completion signal, never evidence of an empty
result. Requests that never reach the interceptor fail with `NOT_OBSERVED`
diagnostics. Close unused captures; fixture reset cancels unsent registrations and
refuses to reset while a captured request is still running, even after abandonment.

The snapshot describes actual calls recorded by the fake. Misk can log and swallow
a transformer or hook failure before the fake is reached, yielding an empty or
partial snapshot. Completion does not prove that every audit hook succeeded or
that a remote audit service accepted an event. Detached producers, scope-close
listeners, and unrelated outer interceptors are outside this boundary. Late writes
through a retained captured scope fail without changing the frozen snapshot; this
does not join arbitrary future producers.
