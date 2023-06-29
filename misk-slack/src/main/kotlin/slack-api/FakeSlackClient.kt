package `slack-api`

import retrofit2.Response
import java.io.IOException
import java.io.UncheckedIOException
import javax.inject.Inject

class FakeSlackClient @Inject constructor(
  private val slackApi: SlackApi
) : SlackClient {
  override fun postMessage(request: PostMessageJson) : PostMessageResponseJson {
    return callSlack{slackApi.postMessage(request).execute()}
  }
  override fun postConfirmation(url: String, request: PostMessageJson) : PostMessageResponseJson {
    return callSlack{slackApi.postConfirmation(url, request).execute()}
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
