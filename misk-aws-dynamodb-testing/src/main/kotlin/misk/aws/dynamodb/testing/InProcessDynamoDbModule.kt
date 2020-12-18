package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal
import com.amazonaws.services.dynamodbv2.local.shared.access.LocalDBClient
import com.amazonaws.services.dynamodbv2.local.shared.access.sqlite.SQLiteDBAccess
import com.amazonaws.services.dynamodbv2.local.shared.jobs.JobsRegister
import com.google.inject.Provides
import java.io.File
import java.util.concurrent.ExecutorService
import javax.inject.Named
import javax.inject.Singleton
import kotlin.reflect.KClass
import misk.ServiceModule
import misk.concurrent.ExecutorServiceFactory
import misk.inject.KAbstractModule
import misk.inject.toKey

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
  constructor(vararg tables: KClass<*>) : this(tables.map { DynamoDbTable(it) })

  override fun configure() {
    for (table in tables) {
      multibind<DynamoDbTable>().toInstance(table)
    }
    install(ServiceModule<InProcessDynamoDbService>())
    install(ServiceModule<CreateTablesService>()
        .dependsOn(InProcessDynamoDbService::class.toKey()))
  }

  @Provides @Singleton
  fun provideSqliteDbAccess(): SQLiteDBAccess {
    Libsqlite4JavaLibraryPathInitializer.init()
    return SQLiteDBAccess(null as File?)
  }

  @Provides @Singleton @Named("InProcessDynamoDb")
  fun provideExecutorService(
    executorServiceFactory: ExecutorServiceFactory
  ): ExecutorService {
    return executorServiceFactory.fixed("InProcessDynamoDb", 10)
  }

  @Provides @Singleton
  fun provideLocalDbClient(
    sqliteDbAccess: SQLiteDBAccess,
    @Named("InProcessDynamoDb") executorService: ExecutorService
  ): AmazonDynamoDBLocal {
    val delayTransientStatuses = false
    val jobsRegister = JobsRegister(executorService, delayTransientStatuses)
    return LocalDBClient(sqliteDbAccess, jobsRegister)
  }

  @Provides @Singleton
  fun provideAmazonDynamoDb(
    amazonDynamoDbLocal: AmazonDynamoDBLocal
  ): AmazonDynamoDB {
    return amazonDynamoDbLocal.amazonDynamoDB()
  }

  @Provides @Singleton
  fun provideAmazonDynamoDbStreams(
    amazonDynamoDbLocal: AmazonDynamoDBLocal
  ): AmazonDynamoDBStreams {
    return amazonDynamoDbLocal.amazonDynamoDBStreams()
  }
}
