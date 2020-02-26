package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException
import com.google.common.util.concurrent.AbstractIdleService
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.logging.getLogger
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Creates an in-memory DynamoDB backed by sqlite4java for local testing.
 */
class EmbeddedDynamoDbModule private constructor(
  private val tables: Collection<KClass<*>>
) : KAbstractModule() {

  override fun configure() {
    AwsDynamoDbLocalTestUtils.initSqLite()

    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(DynamoDbTable(table))
    }

    bind<AmazonDynamoDB>().toProvider(object : Provider<AmazonDynamoDB> {
      override fun get(): AmazonDynamoDB {
        return DynamoDBEmbedded.create().amazonDynamoDB()
      }
    }).asSingleton()

    install(ServiceModule<CreateTablesService>())
  }

  companion object {

    fun withTables(vararg tables: KClass<*>): EmbeddedDynamoDbModule {
      return EmbeddedDynamoDbModule(tables.toList())
    }
  }
}

@Singleton
internal class CreateTablesService @Inject constructor(
  private val dynamoDb: AmazonDynamoDB,
  private val tables: Set<DynamoDbTable>
) : AbstractIdleService() {

  override fun startUp() {
    for (tableName in dynamoDb.listTables().tableNames) {
      dynamoDb.deleteTable(tableName)
    }

    val dynamoDbMapper = DynamoDBMapper(dynamoDb)
    val provisionedThroughput = ProvisionedThroughput(1000L, 1000L)
    for (table in tables) {
      try {
        val createTableRequest = dynamoDbMapper.generateCreateTableRequest(table.tableClass.java)
            .withProvisionedThroughput(provisionedThroughput)
        dynamoDb.createTable(createTableRequest)
      } catch (e: ResourceInUseException) {
        logger.error(e) { "Cannot create table." }
      }
    }
  }

  override fun shutDown() {
    dynamoDb.shutdown()
  }
}

private val logger = getLogger<CreateTablesService>()

/**
 * [DynamoDbTable] is a wrapper class that allows for unqualified binding of dynamo table classes
 * without collision.
 */
internal data class DynamoDbTable(val tableClass: KClass<*>)
