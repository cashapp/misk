package misk.aws.dynamodb.testing

import app.cash.tempest.testing.internal.TestDynamoDbService
import com.google.common.util.concurrent.Service
import com.google.inject.Inject
import com.google.inject.Singleton

/**
 * Thin wrapper to make `TestDynamoDbService`, which is not a @Singleton, compatible with `ServiceModule`.
 */
@Singleton
class TestDynamoDb @Inject constructor(
  val service: TestDynamoDbService
) : Service by service
