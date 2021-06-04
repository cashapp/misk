package misk.slack

import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.environment.Env
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import wisp.deployment.TESTING
import javax.inject.Inject

@MiskTest
class SlackClientTest {
  val mockWebServer = MockWebServer()

  @MiskTestModule val module = TestModule()
  @Inject lateinit var slackClient: SlackClient

  @AfterEach
  internal fun tearDown() {
    mockWebServer.shutdown()
  }

  @Test
  internal fun testPostMessage() {
    mockWebServer.enqueue(MockResponse())

    val postMessage =
      slackClient.postMessage("mgersh", ":sweatingtowelguy:", "kudos", "#misk-discuss")
    assertThat(postMessage).isEqualTo(SlackWebhookResponse.ok)

    val recordedRequest = mockWebServer.takeRequest()
    assertThat(recordedRequest.path).isEqualTo("/secret_webhook_path")
    assertThat(recordedRequest.body.readUtf8()).isEqualTo(
      """{"channel":"#misk-discuss","username":"mgersh","text":"kudos","icon_emoji":":sweatingtowelguy:"}"""
    )
  }

  @Test
  internal fun testDefaultChannelMessage() {
    mockWebServer.enqueue(MockResponse())

    val postMessage = slackClient.postMessage("mgersh", ":sweatingtowelguy:", "kudos")
    assertThat(postMessage).isEqualTo(SlackWebhookResponse.ok)

    val recordedRequest = mockWebServer.takeRequest()
    assertThat(recordedRequest.path).isEqualTo("/secret_webhook_path")
    assertThat(recordedRequest.body.readUtf8()).isEqualTo(
      """{"channel":"#default-channel","username":"mgersh","text":"kudos","icon_emoji":":sweatingtowelguy:"}"""
    )
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "invalid_payload",
      "user_not_found",
      "channel_not_found",
      "channel_is_archived",
      "action_prohibited",
      "missing_text_or_fallback_or_attachments"]
  )
  internal fun testFailedPost(failure: String) {
    mockWebServer.enqueue(MockResponse().setBody(failure).setResponseCode(500))

    val postMessage =
      slackClient.postMessage("mgersh", ":sweatingtowelguy:", "kudos", "#misk-discuss")
    assertThat(postMessage).isEqualTo(SlackWebhookResponse.valueOf(failure))
  }

  @Test
  internal fun testUnknownFailurePost() {
    mockWebServer.enqueue(MockResponse().setBody("unknown_failure").setResponseCode(500))

    val postMessage =
      slackClient.postMessage("mgersh", ":sweatingtowelguy:", "kudos", "#misk-discuss")
    assertThat(postMessage).isNull()
  }

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      mockWebServer.start()

      val env = Env(TESTING.name)
      install(DeploymentModule(TESTING, env))
      install(MiskTestingServiceModule())
      install(
        SlackModule(
          config = SlackConfig(
            baseUrl = mockWebServer.url("/").toString(),
            webhook_path = MiskConfig.RealSecret("secret_webhook_path"),
            default_channel = "#default-channel"
          )
        )
      )
    }
  }
}
