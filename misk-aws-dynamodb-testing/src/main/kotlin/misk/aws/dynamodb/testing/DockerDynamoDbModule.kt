package misk.aws.dynamodb.testing

import misk.ServiceModule
import misk.inject.KAbstractModule
import kotlin.reflect.KClass

/**
 * Spins up a docker container for testing. It clears the table content before each test starts.
 *
 * To use this, add this to each test that needs DynamoDB:
 *
 * ```
 *   @MiskExternalDependency
 *   val dockerDynamoDb = DockerDynamoDb
 * ```
 *
 * Note that this may not be used alongside [LocalDynamoDbModule]. DynamoDB may execute in Docker or
 * in-process, but never both.
 */
class DockerDynamoDbModule(
  private val tables: List<DynamoDbTable>
) : KAbstractModule() {

  constructor(vararg tables: DynamoDbTable) : this(tables.toList())
  constructor(vararg tables: KClass<*>) : this(tables.map { DynamoDbTable(it) })

  override fun configure() {
    install(LocalDynamoDbModule(tables))
    install(ServiceModule<CreateTablesService>())
    bind<LocalDynamoDb>().toInstance(DockerDynamoDb.localDynamoDb)
  }
}
