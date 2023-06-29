package `slack-api`

import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Call
import retrofit2.mock.Calls

@Singleton
class FakeSlackApi @Inject constructor() : SlackApi {
  override fun postMessage(postMessageJson: PostMessageJson): Call<PostMessageResponseJson> {
    return Calls.response(PostMessageResponseJson(ok = true))
  }

  override fun postConfirmation(
    url: String,
    confirmationMessageJson: PostMessageJson,
  ): Call<PostMessageResponseJson> {
    return Calls.response(PostMessageResponseJson(ok = true))
  }
}
