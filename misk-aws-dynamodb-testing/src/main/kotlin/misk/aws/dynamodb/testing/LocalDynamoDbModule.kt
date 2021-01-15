package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.google.inject.Provides
import misk.inject.KAbstractModule
import javax.inject.Singleton

internal class LocalDynamoDbModule(
  private val tables: List<DynamoDbTable>
) : KAbstractModule() {
  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(table)
    }
  }

  @Provides @Singleton
  fun providesAmazonDynamoDB(localDynamoDb: LocalDynamoDb): AmazonDynamoDB {
    return localDynamoDb.connect()
  }

  @Provides @Singleton
  fun providesAmazonDynamoDBStreams(localDynamoDb: LocalDynamoDb): AmazonDynamoDBStreams {
    return localDynamoDb.connectToStreams()
  }
}
