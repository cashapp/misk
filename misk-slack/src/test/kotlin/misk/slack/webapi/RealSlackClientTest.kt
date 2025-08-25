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
import jakarta.inject.Inject
import misk.slack.webapi.helpers.Channel
import misk.slack.webapi.helpers.ConversationTopic
import misk.slack.webapi.helpers.InviteRequest
import misk.slack.webapi.helpers.InviteResponse
import misk.slack.webapi.helpers.LatestMessage
import misk.slack.webapi.helpers.Prefs
import misk.slack.webapi.helpers.SetConversationTopicRequest
import misk.slack.webapi.helpers.SetConversationTopicResponse
import misk.slack.webapi.helpers.TopicPurpose
import misk.slack.webapi.helpers.UserGroup
import misk.slack.webapi.helpers.UserGroupRequest
import misk.slack.webapi.helpers.UserGroupResponse

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

  @Test
  fun `set conversation topic`() {
    server.enqueueTopicResponse(sampleTopicResponse)

    val response = slackApi.setConversationTopic(sampleTopicRequest).execute()
    assertThat(response.isSuccessful).isTrue()
  }

  @Test
  fun `invite user to conversation`() {
    server.enqueueInviteResponse(sampleInviteResponse)

    val response = slackApi.inviteToConversation(sampleInviteRequest).execute()
    assertThat(response.isSuccessful).isTrue()
  }

  @Test
  fun `add user to user group`() {
    server.enqueueUserGroupResponse(sampleUserGroupResponse)

    val response = slackApi.updateUserGroup(sampleUserGroupRequest).execute()
    assertThat(response.isSuccessful).isTrue()
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

    val sampleTopicRequest = SetConversationTopicRequest(
      channel = "#default-channel",
      topic = "This is a sample topic for testing",
    )

    val sampleTopicResponse = SetConversationTopicResponse(
      ok = true,
      channel = ConversationTopic(
        id = "C12345678",
        name = "tips-and-tricks",
        is_channel = true,
        is_group = false,
        is_im = false,
        is_mpim = false,
        is_private = false,
        created = 1649195947,
        is_archived = false,
        is_general = false,
        unlinked = 0,
        name_normalized = " tips -and - tricks",
        is_shared = false,
        is_frozen = false,
        is_org_shared = false,
        is_pending_ext_shared = false,
        pending_shared = emptyList(),
        parent_conversation = null,
        creator = " U12345678 ",
        is_ext_shared = false,
        shared_team_ids = listOf("T12345678"),
        pending_connected_team_ids = emptyList(),
        is_member = true,
        last_read = "1649869848.627809",
        latest = LatestMessage(
          type = "message",
          subtype = "channel_topic",
          ts = "1649952691.429799",
          user = "U12345678",
          text = "set the channel topic: Apply topically for best effects",
          topic = "Apply topically for best effects"
        ),
        unread_count = 1,
        unread_count_display = 0,
        topic = TopicPurpose(
          value = "Apply topically for best effects",
          creator = "U12345678",
          last_set = 1649952691
        ),
        purpose = TopicPurpose(
          value = "",
          creator = "",
          last_set = 0
        ),
        previous_names = emptyList()
      )
    )

    val sampleInviteRequest = InviteRequest(
      channel = "#default-channel",
      users = "U1234567890,U9876543210",
    )

    val sampleInviteResponse = InviteResponse(
      ok = true,
      channel = Channel(
        id = "C012AB3CD",
        name = "general",
        is_channel = true,
        is_group = false,
        is_im = false,
        created = 1449252889,
        creator= "W012A3BCD",
        is_archived= false,
        is_general= true,
        unlinked = 0,
        name_normalized = "general",
        is_read_only = false,
        is_shared = false,
        is_ext_shared = false,
        is_org_shared = false,
        pending_shared= emptyList(),
        is_pending_ext_shared = false,
        is_member = true,
        is_private = false,
        is_mpim = false,
        last_read = "1502126650.228446",
        topic = TopicPurpose(
          value = "For public discussion of generalities",
          creator = "W012A3BCD",
          last_set = 1449709364
        ),
        purpose = TopicPurpose(
          value = "This part of the workspace is for fun. Make fun here.",
          creator = "W012A3BCD",
          last_set = 1449709364
        ),
        previous_names = listOf("specifics", "abstractions", "etc")
      )
    )

    val sampleUserGroupRequest = UserGroupRequest(
      usergroup = "U7654321098",
      users = "U1234567890,U9876543210"
    )

    val sampleUserGroupResponse = UserGroupResponse(
      ok = true,
      usergroup = UserGroup(
        id = "S0616NG6M",
        team_id = "T060R4BHN",
        is_usergroup = true,
        name = "Marketing Team",
        description = "Marketing gurus, PR experts and product advocates.",
        handle = "marketing-team",
        is_external = false,
        date_create = 1447096577,
        date_update = 1447102109,
        date_delete = 0,
        auto_type = null,
        created_by = "U060R4BJ4",
        updated_by = "U060R4BJ4",
        deleted_by = null,
        prefs = Prefs(
          channels= emptyList(),
          groups = emptyList(),
        ),
        users= listOf(
          "U060R4BJ4",
          "U060RNRCZ"
        ),
        user_count = 1,
      )
    )
  }
}
