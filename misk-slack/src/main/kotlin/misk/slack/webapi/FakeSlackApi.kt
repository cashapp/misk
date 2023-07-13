package misk.slack.webapi

import misk.slack.webapi.helpers.PostMessage
import misk.slack.webapi.helpers.PostMessageResponse
import retrofit2.Call
import retrofit2.mock.Calls
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSlackApi @Inject constructor() : SlackApi {
  override fun postMessage(postMessageJson: PostMessage): Call<PostMessageResponse> {
    return Calls.response(PostMessageResponse(ok = true))
  }

  override fun postConfirmation(
    url: String,
    confirmationMessageJson: PostMessage,
  ): Call<PostMessageResponse> {
    return Calls.response(PostMessageResponse(ok = true))
  }
}
