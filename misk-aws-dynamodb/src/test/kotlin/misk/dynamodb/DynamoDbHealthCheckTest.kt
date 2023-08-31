package misk.dynamodb

import com.amazonaws.services.dynamodbv2.AbstractAmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DynamoDbHealthCheckTest {
  private lateinit var amazonDynamoDB: AmazonDynamoDB
  private lateinit var healthyHealthCheck: HealthCheck
  private lateinit var unhealthyHealthCheck: HealthCheck

  @BeforeEach
  fun setup() {
    amazonDynamoDB = FakeAmazonDb()
    healthyHealthCheck = object : HealthCheck {
      override fun status(): HealthStatus = HealthStatus.healthy()
    }
    unhealthyHealthCheck = object : HealthCheck {
      override fun status(): HealthStatus = HealthStatus.unhealthy()
    }
  }

  @Test
  fun `status returns healthy when all tables are healthy`() {
    val tables = listOf(RequiredDynamoDbTable("t1"), RequiredDynamoDbTable("t2"))
    assertTrue(DynamoDbHealthCheck(amazonDynamoDB, tables).status().isHealthy)
  }

  @Test
  fun `status returns healthy when describe table fails`() {
    val tables = listOf(RequiredDynamoDbTable("t1"), RequiredDynamoDbTable(unhealthyTable))
    assertFalse(DynamoDbHealthCheck(amazonDynamoDB, tables).status().isHealthy)
  }

  @Test
  fun `status returns custom unhealthy status when custom health check fails`() {
    val tables = listOf(RequiredDynamoDbTable("UnhealthyTable", unhealthyHealthCheck))
    assertFalse(DynamoDbHealthCheck(amazonDynamoDB, tables).status().isHealthy)
  }

  @Test
  fun `status returns healthy when custom health check is healthy`() {
    val tables = listOf(RequiredDynamoDbTable("HealthyTable", healthyHealthCheck))
    assertTrue(DynamoDbHealthCheck(amazonDynamoDB, tables).status().isHealthy)
  }

  @Test
  fun `status returns healthy when default healt check table and custom health check tables are healthy`() {
    val tables =
      listOf(RequiredDynamoDbTable("t1"), RequiredDynamoDbTable("HealthyTable", healthyHealthCheck))
    assertTrue(DynamoDbHealthCheck(amazonDynamoDB, tables).status().isHealthy)
  }

  companion object {
    const val unhealthyTable = "busyTable"
  }

  internal class FakeAmazonDb : AbstractAmazonDynamoDB() {
    override fun describeTable(tableName: String): DescribeTableResult {
      if (tableName == unhealthyTable) throw AmazonDynamoDBException("DynamoDbHealthCheckTest: Table is in bad state.")
      return DescribeTableResult()
    }
  }
}
