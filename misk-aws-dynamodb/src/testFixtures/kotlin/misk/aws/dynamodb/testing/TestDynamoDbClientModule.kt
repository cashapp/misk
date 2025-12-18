package misk.aws.dynamodb.testing

import app.cash.tempest.testing.TestDynamoDbClient
import app.cash.tempest.testing.TestTable
import app.cash.tempest.testing.internal.DefaultTestDynamoDbClient
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.google.common.util.concurrent.AbstractService
import com.google.inject.Provides
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.ServiceModule
import misk.dynamodb.DynamoDbService
import misk.dynamodb.RequiredDynamoDbTable
import misk.inject.KAbstractModule

class TestDynamoDbClientModule(private val port: Int, private val tables: List<DynamoDbTable>) : KAbstractModule() {

  constructor(port: Int, vararg tables: DynamoDbTable) : this(port, tables.toList())

  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(table)
    }
    bind<DynamoDbService>().to<TestDynamoDbService>()
    install(ServiceModule<DynamoDbService>())
  }

  @Provides
  @Singleton
  fun provideRequiredTables(): List<RequiredDynamoDbTable> = tables.map { RequiredDynamoDbTable(it.tableName) }

  @Provides
  @Singleton
  fun providesTestDynamoDbClient(): TestDynamoDbClient {
    return DefaultTestDynamoDbClient(
      tables = tables.map { TestTable.create(it.tableClass, it.configureTable) },
      port = port,
    )
  }

  @Provides
  @Singleton
  fun providesAmazonDynamoDB(testDynamoDbClient: TestDynamoDbClient): AmazonDynamoDB {
    return testDynamoDbClient.dynamoDb
  }

  @Provides
  @Singleton
  fun providesAmazonDynamoDBStreams(testDynamoDbClient: TestDynamoDbClient): AmazonDynamoDBStreams {
    return testDynamoDbClient.dynamoDbStreams
  }
}

@Singleton
private class TestDynamoDbService @Inject constructor() : AbstractService(), DynamoDbService {
  override fun doStart() = notifyStarted()

  override fun doStop() = notifyStopped()
}
