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
import misk.slack.webapi.helpers.EnterpriseUser
import misk.slack.webapi.helpers.Text
import misk.slack.webapi.helpers.UserData
import misk.slack.webapi.helpers.UserProfile
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


  @Test
  fun `get user by email`() {
    server.enqueueUserResponse(sampleUserData)

    val response = slackApi.getUserByEmail("testuser@company.com").execute()
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

    val sampleUserData = UserData(
        id = "U1234567890",
        team_id = "T1234567890",
        name = "123456",
        deleted = false,
        color = "e0a729",
        real_name = "Test User",
        tz = "America/New_York",
        tz_label = "Eastern Daylight Time",
        tz_offset = -14400,
        profile = UserProfile(
          title = "",
          phone = "",
          skype = "",
          real_name = "Test User",
          real_name_normalized = "Test User",
          display_name = "testuser",
          display_name_normalized = "testuser",
          fields = null,
          status_text = "After hours",
          status_emoji = ":waning_crescent_moon:",
          status_expiration = 0,
          avatar_hash = "1234567890abc",
          email = "testuser@company.com",
          first_name = "Test",
          last_name = "User",
          team = "T1234567890"
        ),
        is_admin = false,
        is_owner = false,
        is_primary_owner = false,
        is_restricted = false,
        is_ultra_restricted = false,
        is_bot = false,
        is_app_user = false,
        updated = 1689282220,
        enterprise_user = EnterpriseUser(
          id = "U1234567890",
          enterprise_id = "E1234567890",
          enterprise_name = "Company Name, Inc.",
          is_admin = false,
          is_owner = false,
          teams = arrayOf(
            "T1234567890",
          )
        )
    )
  }
}
