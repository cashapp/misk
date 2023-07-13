package `slack-api`

import com.squareup.moshi.Moshi
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebAction
import misk.web.toResponseBody
import okhttp3.Headers
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PingSlackAction @Inject constructor(
  private val slackApi: SlackApi,
  moshi: Moshi,
) : WebAction {
  @Get("/cash-app/misk/ping-slack")
  @Unauthenticated
  fun pingSlack(
  ): Response<ResponseBody> {

    //set the channel to the channel ID (ie. "C04PSNFH65Q")
    val postMessageJson = PostMessageJson(
      channel = "test",
      response_type = "in_channel",
      blocks = listOf(
        BlockJson(
        type = "section",
        text = TextJson(
          type = "mrkdwn",
          text = buildMrkdwn { append("this is a test.") }
        )
      )
      )
    )

    val response = slackApi.postMessage(postMessageJson).execute()

    response.checkSuccessful()

    return Response(
      body = "hello slack".toResponseBody(),
      statusCode = HttpURLConnection.HTTP_OK,
      headers = Headers.headersOf()
    )
  }
}