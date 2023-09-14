package misk.dynamodb

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import misk.healthchecks.HealthCheck

fun interface DynamoDBHealthCheckFactory {
  /**
   * Creates a [HealthCheck] instance.
   */
  fun create(dynamoDb: AmazonDynamoDB): HealthCheck
}
