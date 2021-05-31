package misk.aws2.dynamodb.testing

import app.cash.tempest2.testing.JvmDynamoDbServer
import app.cash.tempest2.testing.TestTable
import app.cash.tempest2.testing.internal.TestDynamoDbService
import com.google.inject.Provides
import misk.ServiceModule
import misk.aws2.dynamodb.DynamoDbHealthCheck
import misk.aws2.dynamodb.RequiredDynamoDbTable
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient
import javax.inject.Singleton

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

  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(table)
    }
    multibind<HealthCheck>().to<DynamoDbHealthCheck>()
    install(ServiceModule<TestDynamoDb>())
  }

  @Provides @Singleton
  fun provideRequiredTables(): List<RequiredDynamoDbTable> =
    tables.map { RequiredDynamoDbTable(it.tableName) }

  @Provides @Singleton
  fun providesTestDynamoDb(): TestDynamoDb {
    return TestDynamoDb(
      TestDynamoDbService.create(
        serverFactory = JvmDynamoDbServer.Factory,
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
}
