package misk.aws2.dynamodb.testing

import app.cash.tempest2.testing.JvmDynamoDbServer
import app.cash.tempest2.testing.TestDynamoDbClient
import jakarta.inject.Inject
import java.net.ServerSocket
import java.time.LocalDate
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.services.dynamodb.model.ProjectionType

@MiskTest(startService = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalTestDynamoDbClientModuleTest {
  private val server =
    JvmDynamoDbServer.Factory.create(ServerSocket(0).use { it.localPort }).also { it.startAsync().awaitRunning() }

  @MiskTestModule val module = TestModule(server.port)

  @Inject lateinit var client: TestDynamoDbClient
  @Inject lateinit var testFixtures: Set<TestFixture>

  @AfterAll
  fun stopServer() {
    server.stopAsync().awaitTerminated()
  }

  @Test
  fun `reset truncates tables and indexes`() {
    val movieTable =
      DynamoDbEnhancedClient.builder()
        .dynamoDbClient(client.dynamoDb)
        .build()
        .table(client.tables.single().tableName, AbstractDynamoDbTest.MOVIE_TABLE_SCHEMA)
    val tableCreatedAt =
      client.dynamoDb.describeTable { it.tableName(client.tables.single().tableName) }.table().creationDateTime()

    assertThat(movieTable.scan().items()).isEmpty()

    repeat(30) { index ->
      movieTable.putItem(
        DyMovie().apply {
          name = "Movie $index"
          release_date = LocalDate.of(2000, 1, 1).plusDays(index.toLong())
          directed_by = "Director"
        }
      )
    }
    assertThat(movieTable.scan().items()).hasSize(30)
    assertThat(
        movieTable
          .index("movies.release_date_index")
          .query(QueryConditional.keyEqualTo { it.partitionValue("Director") })
          .flatMap { it.items() }
      )
      .hasSize(30)

    testFixtures.single { it.javaClass.simpleName == "TestDynamoDbFixture" }.reset()

    assertThat(
        client.dynamoDb.describeTable { it.tableName(client.tables.single().tableName) }.table().creationDateTime()
      )
      .isEqualTo(tableCreatedAt)
    assertThat(movieTable.scan().items()).isEmpty()
    assertThat(
        movieTable
          .index("movies.release_date_index")
          .query(QueryConditional.keyEqualTo { it.partitionValue("Director") })
          .flatMap { it.items() }
      )
      .isEmpty()
  }

  class TestModule(private val port: Int) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(
        ExternalTestDynamoDbClientModule(
          port,
          DynamoDbTable("movies", DyMovie::class) { createTableEnhancedRequest ->
            createTableEnhancedRequest.globalSecondaryIndices(
              EnhancedGlobalSecondaryIndex.builder()
                .indexName("movies.release_date_index")
                .projection { it.projectionType(ProjectionType.ALL) }
                .provisionedThroughput { it.readCapacityUnits(40_000L).writeCapacityUnits(40_000L) }
                .build()
            )
          },
        )
      )
    }
  }
}
