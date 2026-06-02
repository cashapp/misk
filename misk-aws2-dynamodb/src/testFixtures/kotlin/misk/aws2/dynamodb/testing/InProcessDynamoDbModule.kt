package misk.aws2.dynamodb.testing

import app.cash.tempest2.testing.JvmDynamoDbServer
import app.cash.tempest2.testing.TestTable
import app.cash.tempest2.testing.internal.TestDynamoDbService
import com.google.common.util.concurrent.AbstractService
import com.google.inject.Provider
import jakarta.inject.Inject
import kotlin.reflect.KClass
import misk.ServiceModule
import misk.aws2.dynamodb.DynamoDbService
import misk.aws2.dynamodb.RequiredDynamoDbTable
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.testing.TestFixture
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient

/**
 * Executes a DynamoDB service in-process per test. It clears the table content before each test starts.
 *
 * Note that this may not be used alongside [DockerDynamoDbModule] and `@MiskExternalDependency DockerDynamoDb`.
 * DynamoDB may execute in Docker or in-process, but never both.
 *
 * This module supports multiple installations with different qualifiers:
 * ```
 * install(InProcessDynamoDbModule(
 *   qualifier = PrimaryDynamoDb::class,
 *   tables = listOf(primaryTable)
 * ))
 * install(InProcessDynamoDbModule(
 *   qualifier = SecondaryDynamoDb::class,
 *   tables = listOf(secondaryTable)
 * ))
 *
 * // Usage:
 * class MyTest @Inject constructor(
 *   @PrimaryDynamoDb val primaryDb: DynamoDbClient,
 *   @SecondaryDynamoDb val secondaryDb: DynamoDbClient
 * )
 * ```
 *
 * All installations share the same underlying TestDynamoDbService instance (since it can only be instantiated once per
 * process), but each creates its own set of tables.
 */
class InProcessDynamoDbModule : KAbstractModule {
  private val qualifier: KClass<out Annotation>?
  private val tables: List<DynamoDbTable>

  constructor(qualifier: KClass<out Annotation>?, tables: List<DynamoDbTable>) {
    this.qualifier = qualifier
    this.tables = tables
  }

  // Backward-compatible constructors (unqualified)
  constructor(tables: List<DynamoDbTable>) : this(null, tables)

  constructor(vararg tables: DynamoDbTable) : this(null, tables.toList())

  override fun configure() {
    // Register tables for this qualifier
    val qualifiedTableMultibinder = newMultibinder<DynamoDbTable>(qualifier)
    for (table in tables) {
      qualifiedTableMultibinder.addBinding().toInstance(table)
    }

    // Also register tables in the unqualified multibinder for the core module
    val tableMultibinder = newMultibinder<DynamoDbTable>()
    for (table in tables) {
      tableMultibinder.addBinding().toInstance(table)
    }

    bind(keyOf<List<RequiredDynamoDbTable>>(qualifier)).toInstance(tables.map { RequiredDynamoDbTable(it.tableName) })

    // Install shared core module that creates TestDynamoDb from all registered tables
    install(InProcessDynamoDbCoreModule)

    val testDynamoDbProvider = getProvider(keyOf<TestDynamoDb>())

    bind(keyOf<DynamoDbClient>(qualifier))
      .toProvider(Provider { testDynamoDbProvider.get().service.client.dynamoDb })
      .asSingleton()

    bind(keyOf<DynamoDbStreamsClient>(qualifier))
      .toProvider(Provider { testDynamoDbProvider.get().service.client.dynamoDbStreams })
      .asSingleton()

    bind(keyOf<DynamoDbService>(qualifier)).toProvider(Provider { InProcessDynamoDbService() }).asSingleton()
    install(ServiceModule<DynamoDbService>(qualifier).dependsOn<TestDynamoDb>())
  }

  private object InProcessDynamoDbCoreModule : KAbstractModule() {
    override fun configure() {
      bind(keyOf<TestDynamoDb>())
        .toProvider(
          object : Provider<TestDynamoDb> {
            @Inject lateinit var allTables: Set<DynamoDbTable>

            override fun get(): TestDynamoDb =
              TestDynamoDb(
                TestDynamoDbService.create(
                  serverFactory = JvmDynamoDbServer.Factory,
                  tables =
                    allTables.map { table ->
                      TestTable.create(table.tableName, table.tableClass) {
                        table.configureTable(it.toBuilder()).build()
                      }
                    },
                  port = null,
                )
              )
          }
        )
        .asSingleton()
      install(ServiceModule<TestDynamoDb>())
      multibind<TestFixture>().to(keyOf<TestDynamoDb>())
    }
  }

  /** This service does nothing; depending on Tempest's [TestDynamoDb] is sufficient. */
  private class InProcessDynamoDbService : AbstractService(), DynamoDbService {
    override fun doStart() = notifyStarted()

    override fun doStop() = notifyStopped()
  }
}
