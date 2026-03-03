package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.model.BillingMode
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import misk.MiskTestingServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule

class MoviesTestModule: KAbstractModule() {

  override fun configure() {
    install(MiskTestingServiceModule())

    install(DockerDynamoDbModule(
        DynamoDbTable(DyMovie::class),
        DynamoDbTable(DyCharacter::class) {
          it.withBillingMode(BillingMode.PAY_PER_REQUEST)
        }
    ))
  }
}
