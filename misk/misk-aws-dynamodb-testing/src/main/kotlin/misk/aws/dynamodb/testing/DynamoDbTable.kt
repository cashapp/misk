package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Use this with [DockerDynamoDbModule] or [InProcessDynamoDbModule] to configure your DynamoDB
 * tables for each test execution.
 *
 * Use [configureTable] to customize the table creation request for testing, such as to configure
 * the secondary indexes required by `ProjectionType.ALL`.
 */
@Deprecated("Replace the dependency on misk-aws-dynamodb-testing with testFixtures(misk-aws-dynamodb)")
data class DynamoDbTable(
  val tableClass: KClass<*>,
  val configureTable: (CreateTableRequest) -> CreateTableRequest = { it }
) {
  val tableName: String
    get() {
      val annotation = tableClass.findAnnotation<DynamoDBTable>()
        ?: throw IllegalStateException("Expected @DynamoDBTable on $tableClass")
      return annotation.tableName
    }
}
