package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.spi.FilterReply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class LogbackLevelFilterTest {

  @Test
  fun `exact match returns ACCEPT for matching level`() {
    val filter = LogbackLevelFilter(mapOf("com.squareup.cash.test.ExactClass" to Level.DEBUG))

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.ExactClass") as Logger

    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.DEBUG, null, null, null))
  }

  @Test
  fun `exact match returns ACCEPT for level above configured`() {
    val filter = LogbackLevelFilter(mapOf("com.squareup.cash.test.ExactClass" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.ExactClass") as Logger

    // DEBUG is higher than TRACE, should ACCEPT
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.DEBUG, null, null, null))
  }

  @Test
  fun `exact match returns NEUTRAL for level below configured`() {
    val filter = LogbackLevelFilter(mapOf("com.squareup.cash.test.ExactClass" to Level.DEBUG))

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.ExactClass") as Logger

    // TRACE is below DEBUG, should return NEUTRAL
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.TRACE, null, null, null))
  }

  @Test
  fun `prefix match returns ACCEPT for matching child logger`() {
    val filter = LogbackLevelFilter(mapOf("com.squareup.cash.dynamodb" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.RealDynamoDbLease") as Logger

    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.TRACE, null, null, null))
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.DEBUG, null, null, null))
  }

  @Test
  fun `most specific prefix wins when multiple prefixes match`() {
    val filter =
      LogbackLevelFilter(mapOf("com.squareup.cash" to Level.DEBUG, "com.squareup.cash.dynamodb.lease" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.RealDynamoDbLease") as Logger

    // More specific prefix (lease) with TRACE should win over less specific (cash) with DEBUG
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.TRACE, null, null, null))
  }

  @Test
  fun `exact match takes precedence over prefix match`() {
    val filter =
      LogbackLevelFilter(
        mapOf("com.squareup.cash" to Level.DEBUG, "com.squareup.cash.dynamodb.lease.RealDynamoDbLease" to Level.TRACE)
      )

    val logger = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.RealDynamoDbLease") as Logger

    // Exact match with TRACE should take precedence
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.TRACE, null, null, null))
  }

  @Test
  fun `no match returns NEUTRAL`() {
    val filter = LogbackLevelFilter(mapOf("com.squareup.cash.dynamodb" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient") as Logger

    // No match at all
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.TRACE, null, null, null))
  }

  @Test
  fun `INFO and higher levels always return NEUTRAL`() {
    val filter = LogbackLevelFilter(mapOf("com.squareup.cash.test.ExactClass" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.ExactClass") as Logger

    // INFO and higher should never interfere
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.INFO, null, null, null))
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.WARN, null, null, null))
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.ERROR, null, null, null))
  }

  @Test
  fun `empty configuration returns NEUTRAL for all`() {
    val filter = LogbackLevelFilter(emptyMap())

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.AnyClass") as Logger

    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.TRACE, null, null, null))
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.DEBUG, null, null, null))
  }

  @Test
  fun `real world scenario - AWS and Cash packages`() {
    val filter =
      LogbackLevelFilter(
        mapOf(
          "com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient" to Level.TRACE,
          "com.squareup.cash.dynamodb.lease" to Level.TRACE,
        )
      )

    val awsLogger = LoggerFactory.getLogger("com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient") as Logger
    val cashLogger = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.RealDynamoDbLease") as Logger

    // AWS logger with exact match should ACCEPT TRACE
    assertEquals(FilterReply.ACCEPT, filter.decide(null, awsLogger, Level.TRACE, null, null, null))

    // Cash logger with prefix match should ACCEPT TRACE
    assertEquals(FilterReply.ACCEPT, filter.decide(null, cashLogger, Level.TRACE, null, null, null))
    assertEquals(FilterReply.ACCEPT, filter.decide(null, cashLogger, Level.DEBUG, null, null, null))
  }

  @Test
  fun `package prefix matches all classes in package`() {
    val filter = LogbackLevelFilter(mapOf("com.squareup.cash.dynamodb.lease" to Level.DEBUG))

    val logger1 = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.RealDynamoDbLease") as Logger
    val logger2 = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.DynamoDbLeaseManager") as Logger
    val logger3 = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.DynamoDbLockClient") as Logger

    // All should match via prefix
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger1, Level.DEBUG, null, null, null))
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger2, Level.DEBUG, null, null, null))
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger3, Level.DEBUG, null, null, null))
  }
}