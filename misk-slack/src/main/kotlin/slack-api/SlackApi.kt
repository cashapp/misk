package `slack-api`

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url


interface SlackApi {

  /**
   * Calls Slack and asks it to post message.
   * https://api.slack.com/methods/chat.postMessage
   */
  @POST("/api/chat.postMessage")
  @Headers(value = ["accept: application/json"])
  fun postMessage(
    @Body postMessageJson: PostMessageJson
  ): Call<PostMessageResponseJson>


  /**
   * Calls Slack and asks it to post a confirmation message to the dynamic URL
   * sent from Slack.
   * https://api.slack.com/interactivity/handling#message_responses
   */
  @POST
  @Headers(value = ["accept: application/json"])
  fun postConfirmation(
    @Url url: String,
    @Body confirmationMessageJson: PostMessageJson
  ): Call<PostMessageResponseJson>
}

fun Response<PostMessageResponseJson>.checkSuccessful() {
  check(isSuccessful) {
    "Slack HTTP call failed: ${errorBody()!!.string()}"
  }

  val postMessageResponseJson = body()!!
  check(postMessageResponseJson.ok) {
    "Slack call failed: $postMessageResponseJson"
  }
}
