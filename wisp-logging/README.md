# wisp-logging

Convenience functions on top of [kotlin-logging](https://github.com/MicroUtils/kotlin-logging/).

## Usage

Use this library to easily set MDC per log entry:

```kotlin
private val logger = getLogger<LoggingTest>()

logger.info(
  "user-id" to "blerb", 
  "alias-id" to "d6F1EF53"
) { "tagged info" }
```

Otherwise, this library falls back to kotlin-logging:

```kotlin
logger.info { "some logs" }
```
