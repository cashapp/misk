package misk.aws2.dynamodb.testing

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey

@DynamoDbBean
class DyCharacter {
  @get:DynamoDbPartitionKey
  var movie_name: String = ""
  @get:DynamoDbSortKey
  var character_name: String = ""
}
