package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.google.inject.Provides
import misk.ServiceModule
import misk.inject.KAbstractModule
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Spins up a docker container for testing. It clears the table content before each test starts.
 */
class DockerDynamoDbModule(
  private val tables: List<KClass<*>>
) : KAbstractModule() {
  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(DynamoDbTable(table))
    }
    install(ServiceModule<CreateTablesService>())
  }

  @Provides @Singleton
  fun providesAmazonDynamoDB(): AmazonDynamoDB {
    return DockerDynamoDb.connect()
  }
}

/**
 * [DynamoDbTable] is a wrapper class that allows for unqualified binding of dynamo table classes
 * without collision.
 */
data class DynamoDbTable(val tableClass: KClass<*>)