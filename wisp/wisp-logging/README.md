# wisp-logging

Convenience functions on top of [kotlin-logging](https://github.com/MicroUtils/kotlin-logging/).

## Usage

### MDC

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

### Log Sampling

Use this library to sample down the volume of logs produced the logger:

```kotlin
private val logger = getLogger<LoggingTest>().sampled()
```

By default, this will logger will be rate limited to 100 logs per second, but a custom
[`Sampler`](../wisp-sampling/README.md) can be provided if a different rate or policy is required:

```kotlin
private val logger = getLogger<LoggingTest>().sampled(Sampler.rateLimiting(500L))
```
