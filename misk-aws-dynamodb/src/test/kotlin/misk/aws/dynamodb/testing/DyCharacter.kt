package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

@DynamoDBTable(tableName = "characters")
class DyCharacter {
  @DynamoDBHashKey(attributeName = "movie_name")
  var movie_name: String = ""

  @DynamoDBRangeKey(attributeName = "character_name")
  var character_name: String = ""
}
