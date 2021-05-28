package misk.aws2.dynamodb.testing

import com.google.inject.Provides
import javax.inject.Singleton
import misk.ServiceModule
import misk.aws2.dynamodb.RequiredDynamoDbTable
import misk.inject.KAbstractModule
import wisp.aws2.dynamodb.testing.LocalDynamoDb

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

  override fun configure() {
    install(LocalDynamoDbModule(tables))
    install(ServiceModule<CreateTablesService>())
    bind<LocalDynamoDb>().toInstance(DockerDynamoDb.localDynamoDb)
  }

  @Provides @Singleton
  fun provideRequiredTables(): List<RequiredDynamoDbTable> =
    tables.map { RequiredDynamoDbTable(it.tableName) }
}
