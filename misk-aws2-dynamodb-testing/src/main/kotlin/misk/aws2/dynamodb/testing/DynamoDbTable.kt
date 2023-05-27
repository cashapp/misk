package misk.aws2.dynamodb.testing

import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest
import kotlin.reflect.KClass

/**
 * Use this with [DockerDynamoDbModule] or [InProcessDynamoDbModule] to configure your DynamoDB
 * tables for each test execution.
 *
 * Use [configureTable] to customize the table creation request for testing, such as to configure
 * the secondary indexes required by `ProjectionType.ALL`.
 */
@Deprecated("Replace the dependency on misk-aws2-dynamodb-testing with testFixtures(misk-aws2-dynamodb)")
data class DynamoDbTable @JvmOverloads constructor(
  val tableName: String,
  val tableClass: KClass<*>,
  val configureTable: (CreateTableEnhancedRequest.Builder) -> CreateTableEnhancedRequest.Builder =
    { it }
)
