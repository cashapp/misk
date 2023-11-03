package com.squareup.exemplar

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

@DynamoDBTable(tableName = "rate_limit_buckets")
class DyRateLimitBucket {
  @DynamoDBHashKey(attributeName = "key")
  var key: String = ""
}
