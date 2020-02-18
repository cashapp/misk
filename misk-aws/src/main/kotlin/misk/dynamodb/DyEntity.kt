package misk.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute

abstract class DyEntity {

  @DynamoDBVersionAttribute
  var version: Long = 0
}
