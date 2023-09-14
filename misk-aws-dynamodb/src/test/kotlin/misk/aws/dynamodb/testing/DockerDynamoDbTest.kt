package misk.aws.dynamodb.testing

import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule

@MiskTest(startService = true)
class DockerDynamoDbTest : AbstractDynamoDbTest() {
  @MiskTestModule val module = TestModule(DockerDynamoDbModule(tables, tableToHealthChecks))

  class TestModule(private val dockerModule: DockerDynamoDbModule) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(dockerModule)
    }
  }
}
