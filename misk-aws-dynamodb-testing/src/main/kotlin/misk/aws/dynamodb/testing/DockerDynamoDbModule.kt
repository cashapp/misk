package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClientBuilder
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest
import com.google.inject.Provides
import misk.ServiceModule
import misk.inject.KAbstractModule
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Spins up a docker container for testing. It clears the table content before each test starts.
 */
class DockerDynamoDbModule(
  private val tables: List<DynamoDbTable>
) : KAbstractModule() {

  constructor(vararg tables: DynamoDbTable) : this(tables.toList())
  constructor(vararg tables: KClass<*>) :
    this(
        tables.map { DynamoDbTable(it) }
    )

  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(table)
    }
    install(ServiceModule<CreateTablesService>())
  }

  @Provides @Singleton
  fun providesAmazonDynamoDB(): AmazonDynamoDB {
    return DockerDynamoDb.connect()
  }

  @Provides @Singleton
  fun providesAmazonDynamoDBStreams(): AmazonDynamoDBStreams {
    return DockerDynamoDb.connectToStreams()
  }
}

/**
 * [DynamoDbTable] is a wrapper class that allows for unqualified binding of
 * dynamo table classes without collision. It also provides the ability to
 * customize the table creation request for testing (ie. if a secondary index
 * required ProjectionType.ALL).
 */
data class DynamoDbTable(
  val tableClass: KClass<*>,
  val configureTable: (CreateTableRequest) -> CreateTableRequest =
      CreateTablesService.CONFIGURE_TABLE_NOOP
)
