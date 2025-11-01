package misk.aws2.dynamodb.testing

import app.cash.tempest2.testing.DockerDynamoDbServer
import app.cash.tempest2.testing.TestTable
import app.cash.tempest2.testing.internal.TestDynamoDbService
import com.google.common.util.concurrent.AbstractService
import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.ServiceModule
import misk.aws2.dynamodb.DynamoDbService
import misk.aws2.dynamodb.RequiredDynamoDbTable
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.testing.TestFixture
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient
import kotlin.reflect.KClass

/**
 * Spins up a docker container for testing. It clears the table content before each test starts.
 *
 * Note that this may not be used alongside [LocalDynamoDbModule]. DynamoDB may execute in Docker or
 * in-process, but never both.
 *
 * This module supports multiple installations with different qualifiers for testing
 * multi-installation scenarios.
 *
 * When [qualifiers] is specified, the database will be bound to multiple qualified clients
 * in addition to the unqualified client. This allows tests to access the same database
 * through different qualifiers.
 */
class DockerDynamoDbModule private constructor(
  private val qualifiers: List<KClass<out Annotation>>,
  private val tables: List<DynamoDbTable>,
) : KAbstractModule() {

  // Backward-compatible constructors (unqualified)
  constructor(tables: List<DynamoDbTable>) : this(emptyList(), tables)
  constructor(vararg tables: DynamoDbTable) : this(emptyList(), tables.toList())

  // Constructor for single qualifier (backwards compatible)
  constructor(qualifier: KClass<out Annotation>?, vararg tables: DynamoDbTable) : this(
    qualifier?.let { listOf(it) } ?: emptyList(),
    tables.toList()
  )

  // Constructor with multiple qualifiers
  constructor(
    qualifiers: List<KClass<out Annotation>>,
    vararg tables: DynamoDbTable
  ) : this(qualifiers, tables.toList())

  override fun configure() {
    // Bind tables as unqualified
    val tableMultibinder = newMultibinder<DynamoDbTable>()
    for (table in tables) {
      tableMultibinder.addBinding().toInstance(table)
    }

    bind(keyOf<List<RequiredDynamoDbTable>>()).toInstance(
      tables.map { RequiredDynamoDbTable(it.tableName) }
    )

    // Create the database instance (unqualified)
    bind(keyOf<TestDynamoDb>()).toProvider(Provider {
      TestDynamoDb(
        TestDynamoDbService.create(
          serverFactory = DockerDynamoDbServer.Factory,
          tables = tables.map { table ->
            TestTable.create(table.tableName, table.tableClass) {
              table.configureTable(it.toBuilder()).build()
            }
          },
          port = null
        )
      )
    }).asSingleton()

    val testDynamoDbProvider = getProvider(keyOf<TestDynamoDb>())

    // Bind unqualified clients
    bind(keyOf<DynamoDbClient>()).toProvider(Provider {
      testDynamoDbProvider.get().service.client.dynamoDb
    }).asSingleton()

    bind(keyOf<DynamoDbStreamsClient>()).toProvider(Provider {
      testDynamoDbProvider.get().service.client.dynamoDbStreams
    }).asSingleton()

    bind(keyOf<DynamoDbService>()).to(keyOf<DockerDynamoDbService>())
    install(ServiceModule<DynamoDbService>().dependsOn<TestDynamoDb>())
    install(ServiceModule<TestDynamoDb>())
    multibind<TestFixture>().to(keyOf<TestDynamoDb>())

    // Bind qualified clients to the same database instance
    for (qualifier in qualifiers) {
      bind(keyOf<DynamoDbClient>(qualifier)).toProvider(Provider {
        testDynamoDbProvider.get().service.client.dynamoDb
      }).asSingleton()

      bind(keyOf<DynamoDbStreamsClient>(qualifier)).toProvider(Provider {
        testDynamoDbProvider.get().service.client.dynamoDbStreams
      }).asSingleton()
    }
  }

  /** This service does nothing; depending on Tempest's [TestDynamoDb] is sufficient. */
  @Singleton
  private class DockerDynamoDbService @Inject constructor() : AbstractService(), DynamoDbService {
    override fun doStart() = notifyStarted()
    override fun doStop() = notifyStopped()
  }
}
