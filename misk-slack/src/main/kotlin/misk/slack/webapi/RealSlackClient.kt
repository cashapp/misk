package misk.slack.webapi

import misk.slack.webapi.helpers.PostMessage
import misk.slack.webapi.helpers.PostMessageResponse
import retrofit2.Response
import java.io.IOException
import java.io.UncheckedIOException
import javax.inject.Inject

class RealSlackClient @Inject constructor(
  private val slackApi: SlackApi,
) : SlackClient {
  override fun postMessage(request: PostMessage): PostMessageResponse {
    return callSlack { slackApi.postMessage(request).execute() }
  }

  override fun postConfirmation(url: String, request: PostMessage): PostMessageResponse {
    return callSlack { slackApi.postConfirmation(url, request).execute() }
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

