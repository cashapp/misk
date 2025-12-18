package misk.ratelimiting.bucket4j.dynamodb.v2.proxymanager

import com.google.inject.Module
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import jakarta.inject.Inject
import kotlin.random.Random
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbStringTestModule
import misk.ratelimiting.bucket4j.dynamodb.v2.modules.DynamoDbStringTestModule.Companion.STRING_TABLE_NAME
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import wisp.ratelimiting.bucket4j.ClockTimeMeter

@MiskTest(startService = true)
class StringDynamoDBProxyManagerTest : BaseDynamoDBProxyManagerTest<String>() {
  @Suppress("unused") @MiskTestModule private val module: Module = DynamoDbStringTestModule()

  @Inject private lateinit var dynamoDbClient: DynamoDbClient

  @Inject private lateinit var fakeClock: FakeClock

  override fun createProxyManager() =
    StringDynamoDBProxyManager(
      dynamoDbClient,
      STRING_TABLE_NAME,
      ClientSideConfig.getDefault().withClientClock(ClockTimeMeter(fakeClock)),
    )

  override fun createRandomKey() = Random.nextLong().toString()
}
