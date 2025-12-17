package misk.slack.webapi

import misk.slack.webapi.helpers.AddReactionRequest
import misk.slack.webapi.helpers.AddReactionResponse
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.GetUserResponse
import misk.slack.webapi.helpers.PostMessageResponse
import retrofit2.Response
import java.io.IOException
import java.io.UncheckedIOException
import jakarta.inject.Inject
import misk.slack.webapi.helpers.GetChatPermalinkResponse
import misk.slack.webapi.helpers.InviteRequest
import misk.slack.webapi.helpers.InviteResponse
import misk.slack.webapi.helpers.SetConversationTopicRequest
import misk.slack.webapi.helpers.SetConversationTopicResponse
import misk.slack.webapi.helpers.UserGroupRequest
import misk.slack.webapi.helpers.UserGroupResponse

class RealSlackClient @Inject constructor(
  private val slackApi: SlackApi,
) : SlackClient {
  override fun postMessage(request: PostMessageRequest): PostMessageResponse {
    return callSlack { slackApi.postMessage(request).execute() }
  }

  override fun postConfirmation(url: String, request: PostMessageRequest): PostMessageResponse {
    return callSlack { slackApi.postConfirmation(url, request).execute() }
  }

  override fun getUserByEmail(email: String): GetUserResponse {
    return callSlack { slackApi.getUserByEmail(email).execute() }
  }

  override fun getUserById(userId: String): GetUserResponse {
    return callSlack { slackApi.getUserById(userId).execute() }
  }

  fun inviteToConversation(request: InviteRequest): InviteResponse {
    return callSlack { slackApi.inviteToConversation(request).execute() }
  }

  fun setConversationTopic(request: SetConversationTopicRequest): SetConversationTopicResponse {
    return callSlack { slackApi.setConversationTopic(request).execute() }
  }

  fun updateUserGroup(request: UserGroupRequest): UserGroupResponse {
    return callSlack { slackApi.updateUserGroup(request).execute() }
  }

  fun addReaction(request: AddReactionRequest): AddReactionResponse {
    return callSlack { slackApi.addReaction(request).execute() }
  }

  fun getChatPermalink(channel: String, message_timestamp: String): GetChatPermalinkResponse {
    return callSlack { slackApi.getChatPermalink(channel, message_timestamp).execute() }
  }

  private fun <T> callSlack(callable: () -> Response<T>): T {
    try {
      val response = callable()
      return response.body()!!
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    }
  }
}
