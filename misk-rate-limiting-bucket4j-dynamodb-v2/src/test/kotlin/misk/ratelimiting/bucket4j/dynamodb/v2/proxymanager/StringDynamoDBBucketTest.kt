package misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager

import com.google.inject.Module
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import jakarta.inject.Inject
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbStringTestModule
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbStringTestModule.Companion.STRING_TABLE_NAME
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import wisp.ratelimiting.bucket4j.ClockTimeMeter
import kotlin.random.Random

@MiskTest(startService = true)
class StringDynamoDBBucketTest : BaseBucketTest<String>() {
  @Suppress("unused")
  @MiskTestModule
  private val module: Module = DynamoDbStringTestModule()

  @Inject private lateinit var dynamoDb: DynamoDbClient

  @Inject private lateinit var fakeClock: FakeClock

  override fun createProxyManager() =
    StringDynamoDBProxyManager(
      dynamoDb,
      STRING_TABLE_NAME,
      ClientSideConfig.getDefault().withClientClock(ClockTimeMeter(fakeClock))
    )

  override fun createRandomKey() = Random.nextLong().toString()
}
