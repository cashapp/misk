package misk.aws.dynamodb.testing

import app.cash.tempest.testing.JvmDynamoDbServer
import app.cash.tempest.testing.TestTable
import app.cash.tempest.testing.internal.TestDynamoDbService
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.google.common.util.concurrent.AbstractService
import com.google.inject.Provides
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import misk.ServiceModule
import misk.dynamodb.DynamoDbHealthCheck
import misk.dynamodb.DynamoDbService
import misk.dynamodb.RequiredDynamoDbTable
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule

/**
 * Executes a DynamoDB service in-process per test. It clears the table content before each test
 * starts.
 *
 * Note that this may not be used alongside [DockerDynamoDbModule] and
 * `@MiskExternalDependency DockerDynamoDb`. DynamoDB may execute in Docker or in-process, but never
 * both.
 */
class InProcessDynamoDbModule(
  private val tables: List<DynamoDbTable>
) : KAbstractModule() {

  constructor(vararg tables: DynamoDbTable) : this(tables.toList())
  constructor(vararg tables: KClass<*>) : this(tables.map { DynamoDbTable(it) })

  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(table)
    }
    multibind<HealthCheck>().to<DynamoDbHealthCheck>()
    bind<DynamoDbService>().to<InProcessDynamoDbService>()
    install(ServiceModule<DynamoDbService>().dependsOn<TestDynamoDb>())
    install(ServiceModule<TestDynamoDb>())
  }

  @Provides @Singleton
  fun providesDynamoDbServiceWrapper(): TestDynamoDb {
    return TestDynamoDb(
      TestDynamoDbService.create(
        serverFactory = JvmDynamoDbServer.Factory,
        tables = tables.map { TestTable.create(it.tableClass, it.configureTable) },
        port = null
      )
    )
  }

  @Provides @Singleton
  fun providesAmazonDynamoDB(testDynamoDb: TestDynamoDb): AmazonDynamoDB {
    return testDynamoDb.service.client.dynamoDb
  }

  @Provides @Singleton
  fun providesAmazonDynamoDBStreams(testDynamoDb: TestDynamoDb): AmazonDynamoDBStreams {
    return testDynamoDb.service.client.dynamoDbStreams
  }

  @Provides @Singleton
  fun provideRequiredTables(): List<RequiredDynamoDbTable> =
    tables.map { RequiredDynamoDbTable(it.tableName) }

  /** This service does nothing; depending on Tempest's [TestDynamoDb] is sufficient. */
  @Singleton
  private class InProcessDynamoDbService @Inject constructor(
  ) : AbstractService(), DynamoDbService {
    override fun doStart() = notifyStarted()
    override fun doStop() = notifyStopped()
  }
}
