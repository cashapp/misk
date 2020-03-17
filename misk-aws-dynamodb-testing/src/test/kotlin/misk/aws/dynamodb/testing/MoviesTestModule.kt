package misk.aws.dynamodb.testing

import misk.MiskTestingServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule

class MoviesTestModule: KAbstractModule() {

  override fun configure() {
    install(MiskTestingServiceModule())
    install(EnvironmentModule(Environment.TESTING))

    install(DockerDynamoDbModule(DyMovie::class, DyCharacter::class))
  }
}
