package misk.aws.dynamodb.testing

import app.cash.tempest.testing.internal.TestDynamoDbService
import com.google.common.util.concurrent.Service
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.testing.TestFixture

/** Thin wrapper to make `TestDynamoDbService`, which is not a @Singleton, compatible with `ServiceModule`. */
@Singleton
@Deprecated(
  message =
    "AWS SDK v1 DynamoDB is deprecated. Use the AWS SDK v2 DynamoDB testing module in " +
      "misk-aws2-dynamodb (misk.aws2.dynamodb.testing.TestDynamoDb) instead."
)
class TestDynamoDb @Inject constructor(val service: TestDynamoDbService) : Service by service, TestFixture {
  override fun reset() {
    service.client.reset()
  }
}
