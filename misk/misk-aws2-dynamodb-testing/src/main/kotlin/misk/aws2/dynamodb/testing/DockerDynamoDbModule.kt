package misk.aws2.dynamodb.testing

import app.cash.tempest2.testing.DockerDynamoDbServer
import app.cash.tempest2.testing.TestTable
import app.cash.tempest2.testing.internal.TestDynamoDbService
import com.google.common.util.concurrent.AbstractService
import com.google.inject.Provides
import javax.inject.Inject
import javax.inject.Singleton
import misk.ServiceModule
import misk.aws2.dynamodb.DynamoDbHealthCheck
import misk.aws2.dynamodb.DynamoDbService
import misk.aws2.dynamodb.RequiredDynamoDbTable
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient

/**
 * Spins up a docker container for testing. It clears the table content before each test starts.
 *
 * Note that this may not be used alongside [LocalDynamoDbModule]. DynamoDB may execute in Docker or
 * in-process, but never both.
 */
class DockerDynamoDbModule(
  private val tables: List<DynamoDbTable>
) : KAbstractModule() {

  constructor(vararg tables: DynamoDbTable) : this(tables.toList())

  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(table)
    }
    multibind<HealthCheck>().to<DynamoDbHealthCheck>()
    bind<DynamoDbService>().to<DockerDynamoDbService>()
    install(ServiceModule<DynamoDbService>().dependsOn<TestDynamoDb>())
    install(ServiceModule<TestDynamoDb>())
  }

  @Provides @Singleton
  fun provideRequiredTables(): List<RequiredDynamoDbTable> =
    tables.map { RequiredDynamoDbTable(it.tableName) }

  @Provides @Singleton
  fun providesTestDynamoDb(): TestDynamoDb {
    return TestDynamoDb(
      TestDynamoDbService.create(
        serverFactory = DockerDynamoDbServer.Factory,
        tables = tables.map { table ->
          TestTable.create(table.tableName, table.tableClass) {
            table.configureTable(it.toBuilder()).build()
          }
        },
        port = null
      )
    )
  }

  @Provides @Singleton
  fun providesAmazonDynamoDB(testDynamoDb: TestDynamoDb): DynamoDbClient {
    return testDynamoDb.service.client.dynamoDb
  }

  @Provides @Singleton
  fun providesAmazonDynamoDBStreams(testDynamoDb: TestDynamoDb): DynamoDbStreamsClient {
    return testDynamoDb.service.client.dynamoDbStreams
  }

  /** This service does nothing; depending on Tempest's [TestDynamoDb] is sufficient. */
  @Singleton
  private class DockerDynamoDbService @Inject constructor() : AbstractService(), DynamoDbService {
    override fun doStart() = notifyStarted()
    override fun doStop() = notifyStopped()
  }
}
