package slack

import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.slack.SlackWebhookResponse
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import `slack-api`.ButtonLinkAndValueJson
import `slack-api`.PostMessageJson
import `slack-api`.RealSlackClient
import `slack-api`.RealSlackClientModule
import `slack-api`.SlackConfig
import wisp.deployment.TESTING
import javax.inject.Inject

@MiskTest
class RealSlackClientTest {
  val mockWebServer = MockWebServer()

  @MiskTestModule val module = TestModule()
  @Inject lateinit var slackClient: RealSlackClient

  @AfterEach
  internal fun tearDown() {
    mockWebServer.shutdown()
  }

  @Test
  internal fun testPostMessage() {
    mockWebServer.enqueue(MockResponse())

    val postMessage =
      slackClient.postMessage(
        PostMessageJson(
          channel = "#default-channel",
          blocks = listOf(
            BlockJson(
              type = "section",
              text = TextJson(
                type = "mrkdwn",
                text = "this is a test",
              )
            )
          )
        )
      )

    Assertions.assertThat(postMessage).isEqualTo(SlackWebhookResponse.ok)

    val recordedRequest = mockWebServer.takeRequest()
    Assertions.assertThat(recordedRequest.path).isEqualTo("/secret_webhook_path")
    Assertions.assertThat(recordedRequest.body.readUtf8()).isEqualTo(
      """{"channel":"#misk-discuss","username":"mgersh","text":"kudos","icon_emoji":":sweatingtowelguy:"}"""
    )
  }

  @Test
  internal fun testDefaultChannelMessage() {
    mockWebServer.enqueue(MockResponse())

    val postMessage = slackClient.postMessage(
      PostMessageJson(
        channel = "#default-channel",
        blocks = listOf(
          BlockJson(
            type = "section",
            text = TextJson(
              type = "mrkdwn",
              text = "this is a test",
            )
          )
        )
      )
    )
    Assertions.assertThat(postMessage).isEqualTo(SlackWebhookResponse.ok)

    val recordedRequest = mockWebServer.takeRequest()
    Assertions.assertThat(recordedRequest.path).isEqualTo("/secret_webhook_path")
    Assertions.assertThat(recordedRequest.body.readUtf8()).isEqualTo(
      """{"channel":"#default-channel","username":"mgersh","text":"kudos","icon_emoji":":sweatingtowelguy:"}"""
    )
  }

  /**
   * An envelope that contains text or elements.
   *
   * https://api.slack.com/reference/block-kit/blocks
   */
  data class BlockJson(
    val type: String,
    val replace_original: Boolean? = null,
    val block_id: String? = null,
    val text: TextJson? = null,
    val accessory: ButtonLinkAndValueJson? = null,
    val elements: List<ButtonLinkAndValueJson>? = null,
  )

  /** https://api.slack.com/reference/block-kit/composition-objects#text */
  data class TextJson(
    val type: String,
    val text: String? = null,
    /** This must be null if [type] is "mrkdwn". */
    val emoji: Boolean? = null,
  )

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      mockWebServer.start()

      install(DeploymentModule(TESTING))
      install(MiskTestingServiceModule())
      install(
        RealSlackClientModule(
          config = SlackConfig(
            url = mockWebServer.url("/").toString(),
            bearer_token = MiskConfig.RealSecret("xoxb-1234567890123-4567890abcdef-FakeSecretFakeSecretFake"),
            signing_secret = MiskConfig.RealSecret("abcdef0123456789abcdef0123456789")
          )
        )
      )
    }
  }
}
