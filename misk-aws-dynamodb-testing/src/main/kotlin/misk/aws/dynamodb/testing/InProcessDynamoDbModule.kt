package misk.aws.dynamodb.testing

import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import kotlin.reflect.KClass

/**
 * Executes a DynamoDB service in-process per test. It clears the table content before each test
 * starts.
 *
 * Note that this may not be used alongside [DockerDynamoDbModule] and
 * `@MiskExternalResource DockerDynamoDb`. DynamoDB may execute in Docker or in-process, but never
 * both.
 */
class InProcessDynamoDbModule(
  private val tables: List<DynamoDbTable>
) : KAbstractModule() {

  constructor(vararg tables: DynamoDbTable) : this(tables.toList())
  constructor(vararg tables: KClass<*>) : this(tables.map { DynamoDbTable(it) })

  override fun configure() {
    install(LocalDynamoDbModule(tables))
    install(ServiceModule<InProcessDynamoDbService>())
    install(ServiceModule<CreateTablesService>()
        .dependsOn(InProcessDynamoDbService::class.toKey()))
  }
}
