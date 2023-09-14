package misk.aws.dynamodb.testing

import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule

@MiskTest(startService = true)
class InProcessDynamoDbTest : AbstractDynamoDbTest() {
  @MiskTestModule val module = TestModule(InProcessDynamoDbModule(tables, tableToHealthChecks))

  class TestModule(private val inProcessModule: InProcessDynamoDbModule) : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(inProcessModule)
    }
  }
}
