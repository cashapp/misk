# wisp-logging-testing

Captures [Logback](https://logback.qos.ch/) logs to make it possible to do assertions on them.

## Usage

The sample code block below assumes [AssertJ](https://joel-costigliola.github.io/assertj/), but
this library is not tied to a particular test framework or assertions library.

```kotlin
class MyClass {
  fun log() {
    val logger = getLogger<MyClass>()
    logger.info("this is a log message!")
  }
}

fun `test that logs are captured`() {
  val logCollector = WispQueuedLogCollector()
  
  // Usually put this in a Before block.
  logCollector.startUp()

  // Test messages:
  MyClass().log()
  // takeMessages and takeEvents consume log entries.
  assertThat(logCollector.takeMessages()).containsExactly("this is a log message!")
  
  // Test other log data, e.g. MDC:
  MyClass().log()
  // Because the first log() call was consumed, takeEvents only returns one event.
  assertThat(logCollector.takeEvents())
    .extracting(Function { it.mdcPropertyMap })
    .containsExactly(mapOf())
  
  // It's also possible to test that nothing is logged.
  assertThat(logCollector.takeMessages()).isEmpty()
  
  // Usually put this in an After block.
  logCollector.shutDown()
}
```
