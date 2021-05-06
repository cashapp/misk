package wisp.aws2.dynamodb.testing

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.model.ProjectionType

internal class DockerDynamoDbTest : AbstractDynamoDbTest() {

  private val moviesTable =
    DynamoDbTable("movies", DyMovie::class) { createTableEnhancedRequest ->
      createTableEnhancedRequest.globalSecondaryIndices(
        EnhancedGlobalSecondaryIndex.builder()
          .indexName("movies.release_date_index")
          .projection { it.projectionType(ProjectionType.ALL) }
          .provisionedThroughput { it.readCapacityUnits(40_000L).writeCapacityUnits(40_000L) }
          .build()
      )
    }

  private val charactersTable = DynamoDbTable("characters", DyCharacter::class)

  lateinit var dockerDynamoDb : DockerDynamoDb
  lateinit var createTablesService: CreateTablesService

  @BeforeEach
  fun `set up dynamodb docker container and create tables before each test`() {
    dockerDynamoDb = DockerDynamoDb()
    dockerDynamoDb.startup()
    dynamoDbClient = dockerDynamoDb.connect()
    tables = setOf(moviesTable, charactersTable)
    createTablesService = CreateTablesService(dynamoDbClient, tables)
    createTablesService.startUp()
  }

  @AfterEach
  fun `stop the dynamodb docker container after each test`() {
    createTablesService.shutDown()
    dockerDynamoDb.shutdown()
  }
}
