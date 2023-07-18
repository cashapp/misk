package misk.slack.webapi

import com.google.inject.name.Names
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientModule
import misk.client.HttpClientsConfig
import misk.client.HttpClientsConfigModule
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.slack.webapi.helpers.Block
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.Text
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import javax.inject.Inject

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


  inner class SlackTestingModule : KAbstractModule() {
    override fun configure() {
      install(LogCollectorModule())
      install(DeploymentModule(TESTING))
      install(MiskTestingServiceModule())
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
    val samplePostMessageJson = PostMessageRequest(
      channel = "#default-channel",
      blocks = listOf(
        Block(
          type = "section",
          text = Text(
            type = "mrkdwn",
            text = "this is a test",
          )
        )
      )
    )
  }
}
