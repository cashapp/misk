package misk.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute

/*
 * This version field is used for optimistic locking.
 */
@DynamoDBTable(tableName = "")
abstract class DyEntity {

  @DynamoDBVersionAttribute(attributeName = "version")
  var version: Long = 0
}
