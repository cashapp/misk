package misk.slack.webapi

import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.UserData
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.slack.webapi.helpers.GetChatPermalinkResponse
import misk.slack.webapi.helpers.InviteResponse
import misk.slack.webapi.helpers.SetConversationTopicResponse
import misk.slack.webapi.helpers.UserGroupResponse

/**
 * Wrap [MockWebServer] to pretend its a Slack server.
 */
@Singleton
class MockSlackServer @Inject constructor(
  moshi: Moshi,
) : AbstractIdleService() {
  private val server = MockWebServer()

  private val messageJsonAdapter = moshi.adapter(PostMessageRequest::class.java)
  private val userJsonAdapter = moshi.adapter(UserData::class.java)
  private val chatPermalinkJsonAdapter = moshi.adapter(GetChatPermalinkResponse::class.java)
  private val topicJsonAdapter = moshi.adapter(SetConversationTopicResponse::class.java)
  private val inviteJsonAdapter = moshi.adapter(InviteResponse::class.java)
  private val usergroupJsonAdapter = moshi.adapter(UserGroupResponse::class.java)

  override fun startUp() {
    server.start()
  }

  override fun shutDown() {
    server.shutdown()
  }

  /** [SlackApi.postMessage] and [SlackApi.postConfirmation] return this */
  fun enqueueMessageResponse(postMessageJson: PostMessageRequest) {
    server.enqueue(
      MockResponse()
        .setBody(messageJsonAdapter.toJson(postMessageJson))
    )
  }

  /** [SlackApi.getUserByEmail] returns this */
  fun enqueueUserResponse(userData: UserData) {
    server.enqueue(
      MockResponse()
        .setBody(userJsonAdapter.toJson(userData))
    )
  }

  /** [SlackApi.getPermalink] returns this */
  fun enqueueChatPermalinkResponse(chatPermalinkData: GetChatPermalinkResponse) {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(chatPermalinkData.toString())
    )
  }

  /** [SlackApi.setConversationTopic] returns this */
  fun enqueueTopicResponse(conversationTopic: SetConversationTopicResponse) {
    server.enqueue(
      MockResponse()
        .setBody(topicJsonAdapter.toJson(conversationTopic))
    )
  }

  /** [SlackApi.inviteToConversation] returns this */
  fun enqueueInviteResponse(channel: InviteResponse) {
    server.enqueue(
      MockResponse()
        .setBody(inviteJsonAdapter.toJson(channel))
    )
  }

  /** [SlackApi.updateUserGroup] returns this */
  fun enqueueUserGroupResponse(usergroup: UserGroupResponse) {
    server.enqueue(
      MockResponse()
        .setBody(usergroupJsonAdapter.toJson(usergroup))
    )
  }
}
