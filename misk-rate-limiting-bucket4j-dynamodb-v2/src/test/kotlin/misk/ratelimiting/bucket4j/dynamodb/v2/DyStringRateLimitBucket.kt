package misk.ratelimiting.bucket4j.dynamodb.v2

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

@DynamoDbBean
class DyStringRateLimitBucket {
  @get:DynamoDbPartitionKey
  var key: String = ""
}
