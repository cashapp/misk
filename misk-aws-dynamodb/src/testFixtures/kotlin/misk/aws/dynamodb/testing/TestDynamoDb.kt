package misk.aws.dynamodb.testing

import app.cash.tempest.testing.internal.TestDynamoDbService
import com.google.common.util.concurrent.Service
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.testing.TestFixture

/** Thin wrapper to make `TestDynamoDbService`, which is not a @Singleton, compatible with `ServiceModule`. */
@Singleton
class TestDynamoDb @Inject constructor(val service: TestDynamoDbService) : Service by service, TestFixture {
  override fun reset() {
    service.client.reset()
  }
}
