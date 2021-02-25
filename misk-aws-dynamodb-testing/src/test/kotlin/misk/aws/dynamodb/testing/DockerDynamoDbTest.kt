package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.model.BillingMode
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule

@MiskTest(startService = true)
class DockerDynamoDbTest : AbstractDynamoDbTest() {

  @MiskTestModule
  val module = TestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())

      install(
        DockerDynamoDbModule(
          DynamoDbTable(DyMovie::class),
          DynamoDbTable(DyCharacter::class) {
            it.withBillingMode(BillingMode.PAY_PER_REQUEST)
          }
        )
      )
    }
  }
}
