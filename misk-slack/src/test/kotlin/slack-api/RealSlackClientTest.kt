package `slack-api`

import com.google.inject.name.Names
import com.google.inject.util.Modules
import com.squareup.cash.treelot.launchdarkly.MockSlackServer
import misk.MiskTestingServiceModule
import misk.config.MiskConfig
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import misk.ServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientsConfig
import misk.client.HttpClientsConfigModule
import misk.environment.DeploymentModule
import misk.logging.LogCollectorModule
import wisp.deployment.TESTING

@MiskTest(startService = true)
class RealSlackClientTest {
  @MiskTestModule val module = object : KAbstractModule() {
    override fun configure() {
      val slackClientModule = RealSlackClientModule(
        SlackConfig(
          bearer_token = MiskConfig.RealSecret("xoxb-1234567890123-4567890abcdef-FakeSecretFakeSecretFake"),
          signing_secret = MiskConfig.RealSecret("abcdef0123456789abcdef0123456789")
        )
      )
      install(Modules.override(SlackTestingModule()).with(slackClientModule))
      install(ServiceModule<MockSlackServer>())
    }
  }

  @Inject lateinit var slackApi: SlackApi
  @Inject lateinit var server: MockSlackServer

  @Test
  fun `post message`() {
    server.enqueueMessageResponse(samplePostMessageJson)

    val response = slackApi.postMessage(samplePostMessageJson).execute()
    assertThat(response.isSuccessful()).isTrue()
  }

  @Test
  fun `post confirmation`() {
    server.enqueueMessageResponse(samplePostMessageJson)

    val response = slackApi.postConfirmation("https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX", samplePostMessageJson).execute()
    assertThat(response.isSuccessful()).isTrue()
  }

  inner class SlackTestingModule : KAbstractModule() {
    override fun configure() {
      install(LogCollectorModule())
      install(DeploymentModule(TESTING))
      install(MiskTestingServiceModule())
      install(FakeSlackClientModule())
      install(
        HttpClientsConfigModule(
          HttpClientsConfig(
            endpoints = mapOf(
              "slack" to HttpClientEndpointConfig("https://hooks.slack.com/"),
            )
          ),
      )
      )
      install(
        HttpClientModule(
          "slack",
          Names.named("slack")
        )
      )
    }
  }

  companion object {
    val samplePostMessageJson = PostMessageJson(
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
  }
}
