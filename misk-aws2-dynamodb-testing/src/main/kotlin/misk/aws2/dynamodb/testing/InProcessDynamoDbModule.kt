package misk.aws2.dynamodb.testing

import com.amazonaws.services.dynamodbv2.local.main.ServerRunner
import com.amazonaws.services.dynamodbv2.local.server.DynamoDBProxyServer
import com.google.inject.Provides
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.toKey
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
    install(LocalDynamoDbModule(tables))
    install(ServiceModule<InProcessDynamoDbService>())
    install(ServiceModule<CreateTablesService>()
        .dependsOn(InProcessDynamoDbService::class.toKey()))
  }

  @Provides @Singleton
  internal fun provideDynamoDBProxyServer(localDynamoDb: LocalDynamoDb): DynamoDBProxyServer {
    Libsqlite4JavaLibraryPathInitializer.init()
    return ServerRunner.createServerFromCommandLineArgs(
        arrayOf("-inMemory", "-port", localDynamoDb.url.port.toString()))
  }
}
