package misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager

import com.google.inject.Module
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import jakarta.inject.Inject
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbLongTestModule
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbLongTestModule.Companion.LONG_TABLE_NAME
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import wisp.ratelimiting.bucket4j.ClockTimeMeter
import kotlin.random.Random

@MiskTest(startService = true)
class LongDynamoDBBucketTest : BaseBucketTest<Long>() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = DynamoDbLongTestModule()

  @Inject private lateinit var dynamoDb: DynamoDbClient

  @Inject private lateinit var fakeClock: FakeClock

  override fun createProxyManager() =
    LongDynamoDBProxyManager(
      dynamoDb,
      LONG_TABLE_NAME,
      ClientSideConfig.getDefault().withClientClock(ClockTimeMeter(fakeClock))
    )

  override fun createRandomKey() = Random.nextLong()
}
