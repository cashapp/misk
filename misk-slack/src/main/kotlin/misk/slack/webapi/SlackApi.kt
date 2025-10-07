package misk.slack.webapi

import misk.slack.webapi.helpers.GetChatPermalinkResponse
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.PostMessageResponse
import misk.slack.webapi.helpers.GetUserResponse
import misk.slack.webapi.helpers.InviteRequest
import misk.slack.webapi.helpers.InviteResponse
import misk.slack.webapi.helpers.SetConversationTopicRequest
import misk.slack.webapi.helpers.SetConversationTopicResponse
import misk.slack.webapi.helpers.UserGroupRequest
import misk.slack.webapi.helpers.UserGroupResponse
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface SlackApi {
  /**
   * Calls Slack and asks it to post message.
   * https://api.slack.com/methods/chat.postMessage
   */
  @POST("/api/chat.postMessage")
  @Headers(value = ["accept: application/json"])
  fun postMessage(
    @Body postMessageJson: PostMessageRequest,
  ): Call<PostMessageResponse>

  /**
   * Calls Slack and asks it to post a confirmation message to the dynamic URL
   * sent from Slack.
   * https://api.slack.com/interactivity/handling#message_responses
   */
  @POST
  @Headers(value = ["accept: application/json"])
  fun postConfirmation(
    @Url url: String,
    @Body confirmationMessageJson: PostMessageRequest,
  ): Call<PostMessageResponse>

  /**
   * Calls Slack to fetch user for given email.
   *
   * https://api.slack.com/methods/users.lookupByEmail
   */
  @GET("/api/users.lookupByEmail")
  @Headers(value = ["accept: application/json"])
  fun getUserByEmail(@Query("email") email: String): Call<GetUserResponse>

  /**
   * Calls Slack and asks it to set a channel topic.
   * https://slack.com/api/conversations.setTopic
   */
  @POST("/api/conversations.setTopic")
  @Headers(value = ["accept: application/json"])
  fun setConversationTopic(
    @Body setConversationTopicJson: SetConversationTopicRequest,
  ): Call<SetConversationTopicResponse>

  /**
   * Calls Slack and asks it to invite a user to a conversation.
   * https://slack.com/api/conversations.invite
   */
  @POST("/api/conversations.invite")
  @Headers(value = ["accept: application/json"])
  fun inviteToConversation(
    @Body inviteRequestJson: InviteRequest,
  ): Call<InviteResponse>

  /**
   * Calls Slack and asks it to update the users in the usergroup
   * https://slack.com/api/usergroups.users.update
   */
  @POST("/api/usergroups.users.update")
  @Headers(value = ["accept: application/json"])
  fun updateUserGroup(
    @Body updateRequestJson: UserGroupRequest,
  ): Call<UserGroupResponse>

  /**
   * Calls Slack and retrieve a permalink URL for a specific extant message
   * https://slack.com/api/chat.getPermalink/
   */
  @GET("/api/chat.getPermalink")
  @Headers(value = ["accept: application/json"])
  fun getChatPermalink(
    @Query("channel") channel: String,
    @Query("message_ts") ts: String
  ): Call<GetChatPermalinkResponse>
}

fun Response<PostMessageResponse>.checkSuccessful() {
  check(isSuccessful) {
    "Slack HTTP call failed: ${errorBody()!!.string()}"
  }

  val postMessageResponseJson = body()!!
  check(postMessageResponseJson.ok) {
    "Slack call failed: $postMessageResponseJson"
  }
}
