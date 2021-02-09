package misk.aws2.dynamodb.testing

import com.google.inject.Provides
import misk.inject.KAbstractModule
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient
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
  fun providesDynamoDbClient(localDynamoDb: LocalDynamoDb): DynamoDbClient {
    return localDynamoDb.connect()
  }

  @Provides @Singleton
  fun providesDynamoDbStreamsClient(localDynamoDb: LocalDynamoDb): DynamoDbStreamsClient {
    return localDynamoDb.connectToStreams()
  }
}
