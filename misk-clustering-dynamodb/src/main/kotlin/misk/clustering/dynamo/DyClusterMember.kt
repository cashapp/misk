package misk.clustering.dynamo

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

@DynamoDbBean
internal class DyClusterMember {
  @get:DynamoDbPartitionKey var name: String? = null

  // Stored as epoch ms
  @get:DynamoDbAttribute("updated_at") var updated_at: Long? = null

  @get:DynamoDbAttribute("pod_name") var pod_name: String? = null

  // Stored as epoch sec
  @get:DynamoDbAttribute("expires_at") var expires_at: Long? = null
}
