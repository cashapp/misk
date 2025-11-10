package misk.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.core.spi.FilterReply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class LogLevelFilterTest {

  @Test
  fun `exact match returns ACCEPT for matching level`() {
    val filter = LogLevelFilter(mapOf("com.squareup.cash.test.ExactClass" to Level.DEBUG))

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.ExactClass") as Logger

    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.DEBUG, null, null, null))
  }

  @Test
  fun `exact match returns ACCEPT for level above configured`() {
    val filter = LogLevelFilter(mapOf("com.squareup.cash.test.ExactClass" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.ExactClass") as Logger

    // DEBUG is higher than TRACE, should ACCEPT
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.DEBUG, null, null, null))
  }

  @Test
  fun `exact match returns NEUTRAL for level below configured`() {
    val filter = LogLevelFilter(mapOf("com.squareup.cash.test.ExactClass" to Level.DEBUG))

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.ExactClass") as Logger

    // TRACE is below DEBUG, should return NEUTRAL
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.TRACE, null, null, null))
  }

  @Test
  fun `prefix match returns ACCEPT for matching child logger`() {
    val filter = LogLevelFilter(mapOf("com.squareup.cash.dynamodb" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.RealDynamoDbLease") as Logger

    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.TRACE, null, null, null))
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.DEBUG, null, null, null))
  }

  @Test
  fun `most specific prefix wins when multiple prefixes match`() {
    val filter =
      LogLevelFilter(mapOf("com.squareup.cash" to Level.DEBUG, "com.squareup.cash.dynamodb.lease" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.RealDynamoDbLease") as Logger

    // More specific prefix (lease) with TRACE should win over less specific (cash) with DEBUG
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.TRACE, null, null, null))
  }

  @Test
  fun `exact match takes precedence over prefix match`() {
    val filter =
      LogLevelFilter(
        mapOf("com.squareup.cash" to Level.DEBUG, "com.squareup.cash.dynamodb.lease.RealDynamoDbLease" to Level.TRACE)
      )

    val logger = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.RealDynamoDbLease") as Logger

    // Exact match with TRACE should take precedence
    assertEquals(FilterReply.ACCEPT, filter.decide(null, logger, Level.TRACE, null, null, null))
  }

  @Test
  fun `no match returns NEUTRAL`() {
    val filter = LogLevelFilter(mapOf("com.squareup.cash.dynamodb" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient") as Logger

    // No match at all
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.TRACE, null, null, null))
  }

  @Test
  fun `INFO and higher levels always return NEUTRAL`() {
    val filter = LogLevelFilter(mapOf("com.squareup.cash.test.ExactClass" to Level.TRACE))

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.ExactClass") as Logger

    // INFO and higher should never interfere
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.INFO, null, null, null))
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.WARN, null, null, null))
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.ERROR, null, null, null))
  }

  @Test
  fun `empty configuration returns NEUTRAL for all`() {
    val filter = LogLevelFilter(emptyMap())

    val logger = LoggerFactory.getLogger("com.squareup.cash.test.AnyClass") as Logger

    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.TRACE, null, null, null))
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger, Level.DEBUG, null, null, null))
  }

  @Test
  fun `prefix does not match class with similar name`() {
    val filter = LogLevelFilter(mapOf("com.example.Foo" to Level.DEBUG))

    val fooLogger = LoggerFactory.getLogger("com.example.Foo") as Logger
    val fooFactoryLogger = LoggerFactory.getLogger("com.example.FooFactory") as Logger
    val foobarLogger = LoggerFactory.getLogger("com.example.Foobar") as Logger

    // Exact match should work for com.example.Foo
    assertEquals(FilterReply.ACCEPT, filter.decide(null, fooLogger, Level.DEBUG, null, null, null))

    // Should NOT match com.example.FooFactory (no dot after Foo)
    assertEquals(
      FilterReply.NEUTRAL,
      filter.decide(null, fooFactoryLogger, Level.DEBUG, null, null, null)
    )

    // Should NOT match com.example.Foobar (no dot after Foo)
    assertEquals(
      FilterReply.NEUTRAL,
      filter.decide(null, foobarLogger, Level.DEBUG, null, null, null)
    )
  }

  @Test
  fun `prefix matches inner classes and subpackages`() {
    val filter = LogLevelFilter(mapOf("com.example.foo" to Level.DEBUG))

    val innerClassLogger = LoggerFactory.getLogger("com.example.foo.InnerClass") as Logger
    val subpackageLogger = LoggerFactory.getLogger("com.example.foo.subpackage.Bar") as Logger

    // Should match inner classes (com.example.foo.InnerClass)
    assertEquals(
      FilterReply.ACCEPT,
      filter.decide(null, innerClassLogger, Level.DEBUG, null, null, null)
    )

    // Should match subpackages (com.example.foo.subpackage.Bar)
    assertEquals(
      FilterReply.ACCEPT,
      filter.decide(null, subpackageLogger, Level.DEBUG, null, null, null)
    )
  }

  @Test
  fun `prefix does not match partial package names`() {
    val filter = LogLevelFilter(mapOf("com.squareup.cash" to Level.DEBUG))

    val cashLogger = LoggerFactory.getLogger("com.squareup.cash.SomeClass") as Logger
    val cashierLogger = LoggerFactory.getLogger("com.squareup.cashier.SomeClass") as Logger

    // Should match com.squareup.cash.SomeClass
    assertEquals(FilterReply.ACCEPT, filter.decide(null, cashLogger, Level.DEBUG, null, null, null))

    // Should NOT match com.squareup.cashier.SomeClass (no dot after 'cash')
    assertEquals(
      FilterReply.NEUTRAL,
      filter.decide(null, cashierLogger, Level.DEBUG, null, null, null)
    )
  }

  @Test
  fun `package boundary matching with real world examples`() {
    val filter = LogLevelFilter(
      mapOf(
        "com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient" to Level.TRACE,
        "com.squareup.cash.dynamodb.lease" to Level.TRACE,
        "org.hibernate" to Level.DEBUG
      )
    )

    // Exact match for AWS logger
    val awsLogger = LoggerFactory.getLogger("com.amazonaws.services.dynamodbv2.AmazonDynamoDBLockClient") as Logger
    assertEquals(FilterReply.ACCEPT, filter.decide(null, awsLogger, Level.TRACE, null, null, null))

    // Prefix match for Cash logger
    val cashLogger = LoggerFactory.getLogger("com.squareup.cash.dynamodb.lease.RealDynamoDbLease") as Logger
    assertEquals(FilterReply.ACCEPT, filter.decide(null, cashLogger, Level.TRACE, null, null, null))
    assertEquals(FilterReply.ACCEPT, filter.decide(null, cashLogger, Level.DEBUG, null, null, null))

    // Prefix match for Hibernate
    val hibernateLogger = LoggerFactory.getLogger("org.hibernate.SQL") as Logger
    assertEquals(FilterReply.ACCEPT, filter.decide(null, hibernateLogger, Level.DEBUG, null, null, null))

    // Should NOT match similar but different packages
    val dynamodbv3Logger = LoggerFactory.getLogger("com.amazonaws.services.dynamodbv3.Client") as Logger
    val hibernateUtilLogger = LoggerFactory.getLogger("org.hibernateutil.SomeClass") as Logger
    
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, dynamodbv3Logger, Level.TRACE, null, null, null))
    assertEquals(FilterReply.NEUTRAL, filter.decide(null, hibernateUtilLogger, Level.DEBUG, null, null, null))
  }
}