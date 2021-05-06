package misk.aws2.dynamodb.testing

import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.AfterAll
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.model.ProjectionType

@MiskTest(startService = true)
class DockerDynamoDbTest : AbstractDynamoDbTest() {

  @MiskTestModule
  val module = TestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  companion object {
    @AfterAll
    fun shutdownDockerDynamoDb() {
      DockerDynamoDb.shutdown()
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())

      install(
        DockerDynamoDbModule(
          DynamoDbTable("movies", DyMovie::class) { createTableEnhancedRequest ->
            createTableEnhancedRequest.globalSecondaryIndices(
              EnhancedGlobalSecondaryIndex.builder()
                .indexName("movies.release_date_index")
                .projection { it.projectionType(ProjectionType.ALL) }
                .provisionedThroughput { it.readCapacityUnits(40_000L).writeCapacityUnits(40_000L) }
                .build()
            )
          },
          DynamoDbTable("characters", DyCharacter::class)
        )
      )
    }
  }
}
