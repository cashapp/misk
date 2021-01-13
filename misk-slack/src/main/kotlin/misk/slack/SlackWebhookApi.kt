package misk.slack

import com.squareup.moshi.JsonClass
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface SlackWebhookApi {
  @POST
  fun post(@Url url: String, @Body request: SlackWebhookRequest): Call<Void>
}

@JsonClass(generateAdapter = true)
data class SlackWebhookRequest(
  val channel: String,
  val username: String,
  val text: String,
  val icon_emoji: String
)

@JsonClass(generateAdapter = true)
enum class SlackWebhookResponse {
  ok,
  invalid_payload,
  user_not_found,
  channel_not_found,
  channel_is_archived,
  action_prohibited,
  missing_text_or_fallback_or_attachments,
  ;
}
