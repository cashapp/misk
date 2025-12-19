package misk.aws2.dynamodb.testing

import app.cash.tempest2.testing.TestDynamoDbClient
import app.cash.tempest2.testing.TestTable
import app.cash.tempest2.testing.internal.DefaultTestDynamoDbClient
import app.cash.tempest2.testing.internal.createTable
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.AbstractService
import com.google.inject.Provides
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.ServiceModule
import misk.aws2.dynamodb.DynamoDbService
import misk.aws2.dynamodb.RequiredDynamoDbTable
import misk.aws2.dynamodb.TableNameMapper
import misk.inject.KAbstractModule
import misk.testing.TestFixture
import misk.testing.updateForParallelTests
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient

/**
 * Unlike the InProcessDynamoDbModule and DockerDynamoDbModule classes, this module does not internally start DynamoDB,
 * and instead relies on an external DynamoDB server already running on the given port, e.g. using a Gradle task as a
 * dependency of the test task, which starts DynamoDB in a Docker container.
 */
class ExternalTestDynamoDbClientModule(private val port: Int, originalTables: List<DynamoDbTable>) : KAbstractModule() {

  private val tables = originalTables.map { it.copy(tableName = ParallelTestsTableNameMapper.mapName(it.tableName)) }

  constructor(port: Int, vararg tables: DynamoDbTable) : this(port, tables.toList())

  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(table)
    }
    bind<DynamoDbService>().to<TestDynamoDbService>()
    install(ServiceModule<DynamoDbService>().dependsOn<TestDynamoDbFixture>())
    install(ServiceModule<TestDynamoDbFixture>())
    multibind<TestFixture>().to<TestDynamoDbFixture>()
  }

  @Provides
  @Singleton
  fun provideRequiredTables(): List<RequiredDynamoDbTable> = tables.map { RequiredDynamoDbTable(it.tableName) }

  @Provides
  @Singleton
  fun providesTestDynamoDbClient(): TestDynamoDbClient =
    DefaultTestDynamoDbClient(
      tables =
        tables.map { table ->
          TestTable.create(table.tableName, table.tableClass) { table.configureTable(it.toBuilder()).build() }
        },
      port,
    )

  @Provides
  @Singleton
  fun providesAmazonDynamoDB(testDynamoDbClient: TestDynamoDbClient): DynamoDbClient = testDynamoDbClient.dynamoDb

  @Provides
  @Singleton
  fun providesAmazonDynamoDBStreams(testDynamoDbClient: TestDynamoDbClient): DynamoDbStreamsClient =
    testDynamoDbClient.dynamoDbStreams
}

@Singleton
private class TestDynamoDbFixture
@Inject
constructor(private val client: TestDynamoDbClient, private val tables: List<DynamoDbTable>) :
  AbstractIdleService(), TestFixture {

  override fun startUp() {
    reset()
  }

  override fun shutDown() {}

  override fun reset() {
    for (tableName in tables.map { it.tableName }) {
      try {
        client.dynamoDb.deleteTable(DeleteTableRequest.builder().tableName(tableName).build())
      } catch (e: ResourceNotFoundException) {
        // Ignore if the table doesn't exist
      }
    }
    for (table in
      tables.map { table ->
        TestTable.create(table.tableName, table.tableClass) { table.configureTable(it.toBuilder()).build() }
      }) {
      client.dynamoDb.createTable(table)
    }
  }
}

@Singleton
private class TestDynamoDbService @Inject constructor() : AbstractService(), DynamoDbService {
  override fun doStart() = notifyStarted()

  override fun doStop() = notifyStopped()
}

/**
 * A [TableNameMapper] that appends a unique identifier for each test process ID to the table name. This is used to
 * ensure that multiple tests can run in parallel without clobbering each other's tables.
 */
object ParallelTestsTableNameMapper : TableNameMapper {
  override fun mapName(tableName: String): String {
    return tableName.updateForParallelTests { name, index -> name + "_$index" }
  }
}
