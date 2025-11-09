package misk.logging

import ch.qos.logback.classic.Level
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DynamicLogParserTest {

  @Test
  fun `parses single logger with TRACE level`() {
    val result = DynamicLogParser.parseLoggerToLevelPairs("com.squareup.cash.test=TRACE")

    assertEquals(1, result.size)
    assertEquals(Level.TRACE, result["com.squareup.cash.test"])
  }

  @Test
  fun `parses single logger with DEBUG level`() {
    val result = DynamicLogParser.parseLoggerToLevelPairs("com.squareup.cash.test=DEBUG")

    assertEquals(1, result.size)
    assertEquals(Level.DEBUG, result["com.squareup.cash.test"])
  }

  @Test
  fun `parses multiple loggers`() {
    val result =
      DynamicLogParser.parseLoggerToLevelPairs(
        "com.squareup.cash.test=TRACE,com.amazonaws.services=DEBUG,org.example.app=TRACE"
      )

    assertEquals(3, result.size)
    assertEquals(Level.TRACE, result["com.squareup.cash.test"])
    assertEquals(Level.DEBUG, result["com.amazonaws.services"])
    assertEquals(Level.TRACE, result["org.example.app"])
  }

  @Test
  fun `handles whitespace around entries`() {
    val result =
      DynamicLogParser.parseLoggerToLevelPairs("  com.squareup.cash.test = TRACE  ,  com.amazonaws.services = DEBUG  ")

    assertEquals(2, result.size)
    assertEquals(Level.TRACE, result["com.squareup.cash.test"])
    assertEquals(Level.DEBUG, result["com.amazonaws.services"])
  }

  @Test
  fun `ignores empty or null input`() {
    assertEquals(emptyMap<String, Level>(), DynamicLogParser.parseLoggerToLevelPairs(""))
    assertEquals(emptyMap<String, Level>(), DynamicLogParser.parseLoggerToLevelPairs("   "))
  }

  @Test
  fun `ignores invalid log levels`() {
    val result =
      DynamicLogParser.parseLoggerToLevelPairs("com.squareup.cash.valid=TRACE,com.squareup.cash.invalid=INFO")

    // Only TRACE and DEBUG are allowed, INFO should be ignored
    assertEquals(1, result.size)
    assertEquals(Level.TRACE, result["com.squareup.cash.valid"])
  }

  @Test
  fun `ignores unsupported log levels WARN and ERROR`() {
    val result = DynamicLogParser.parseLoggerToLevelPairs("com.test.warn=WARN,com.test.error=ERROR")

    // WARN and ERROR are valid Logback levels but not allowed (only TRACE/DEBUG)
    assertTrue(result.isEmpty())
  }

  @Test
  fun `ignores malformed entries`() {
    val result =
      DynamicLogParser.parseLoggerToLevelPairs(
        "com.squareup.cash.valid=TRACE,invalid_entry,com.squareup.cash.valid2=DEBUG"
      )

    // Malformed entry should be ignored
    assertEquals(2, result.size)
    assertEquals(Level.TRACE, result["com.squareup.cash.valid"])
    assertEquals(Level.DEBUG, result["com.squareup.cash.valid2"])
  }

  @Test
  fun `ignores entries with empty prefix`() {
    val result = DynamicLogParser.parseLoggerToLevelPairs("com.squareup.cash.valid=TRACE,=DEBUG")

    assertEquals(1, result.size)
    assertEquals(Level.TRACE, result["com.squareup.cash.valid"])
  }

  @Test
  fun `handles mix of valid and invalid entries`() {
    val result = DynamicLogParser.parseLoggerToLevelPairs("com.test.valid=TRACE,invalid_format,com.test.valid2=INFO")

    // Malformed entry and unsupported INFO level should be ignored
    assertEquals(1, result.size)
    assertEquals(Level.TRACE, result["com.test.valid"])
  }

  @Test
  fun `handles case insensitive level names`() {
    val result =
      DynamicLogParser.parseLoggerToLevelPairs("com.test.lower=trace,com.test.upper=DEBUG,com.test.mixed=TrAcE")

    assertEquals(3, result.size)
    assertEquals(Level.TRACE, result["com.test.lower"])
    assertEquals(Level.DEBUG, result["com.test.upper"])
    assertEquals(Level.TRACE, result["com.test.mixed"])
  }

  @Test
  fun `handles package prefixes`() {
    val result =
      DynamicLogParser.parseLoggerToLevelPairs("com.squareup.cash=DEBUG,com.amazonaws.services.dynamodbv2=TRACE")

    assertEquals(2, result.size)
    assertEquals(Level.DEBUG, result["com.squareup.cash"])
    assertEquals(Level.TRACE, result["com.amazonaws.services.dynamodbv2"])
  }

  @Test
  fun `handles fully qualified class names`() {
    val result = DynamicLogParser.parseLoggerToLevelPairs("com.squareup.cash.dynamodb.lease.RealDynamoDbLease=TRACE")

    assertEquals(1, result.size)
    assertEquals(Level.TRACE, result["com.squareup.cash.dynamodb.lease.RealDynamoDbLease"])
  }

  @Test
  fun `last entry wins for duplicate prefixes`() {
    val result = DynamicLogParser.parseLoggerToLevelPairs("com.squareup.cash.test=TRACE,com.squareup.cash.test=DEBUG")

    assertEquals(1, result.size)
    assertEquals(Level.DEBUG, result["com.squareup.cash.test"])
  }
}
