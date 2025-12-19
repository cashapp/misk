package misk.slack.webapi

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.HttpURLConnection
import misk.config.MiskConfig.RealSecret
import misk.security.authz.Unauthenticated
import misk.slack.webapi.helpers.Block
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.Text
import misk.slack.webapi.helpers.buildMrkdwn
import misk.slack.webapi.interceptors.SlackClientInterceptor
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebAction
import misk.web.toResponseBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class PingSlackActionTest {
  private val server = MockWebServer()

  private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

  private val slackConfig =
    SlackConfig(
      // ⚠️ DO NOT CHECK IN PRODUCTION KEYS!
      // The 'Bot User OAuth Token', here: https://api.slack.com/apps/A04NSLF2N11/oauth
      bearer_token = RealSecret("xoxb-1234567890123-4567890abcdef-FakeSecretFakeSecretFake"),

      // ⚠️ DO NOT CHECK IN PRODUCTION KEYS!
      // The 'Signing Secret', here: https://api.slack.com/apps/A04NSLF2N11/general
      signing_secret = RealSecret("abcdef0123456789abcdef0123456789"),
    )

  private val pingSlackAction =
    PingSlackAction(
      slackApi =
        Retrofit.Builder()
          .client(OkHttpClient.Builder().addInterceptor(SlackClientInterceptor(slackConfig)).build())
          .baseUrl(server.url("https://slack.com/"))
          .addConverterFactory(MoshiConverterFactory.create(moshi))
          .build()
          .create(SlackApi::class.java),
      moshi = moshi,
    )

  /**
   * This is a facet for manual testing. With the correct secrets, this can validate that messages are being delivered
   * to the intended slack channel.
   */
  @Test
  @Disabled
  fun test() {
    val pingSlack = pingSlackAction.pingSlack()
    println(pingSlack)
  }

  @Singleton
  internal class PingSlackAction @Inject constructor(private val slackApi: SlackApi, moshi: Moshi) : WebAction {
    @Get("/cash-app/misk/ping-slack")
    @Unauthenticated
    fun pingSlack(): Response<ResponseBody> {

      // set the channel to the channel ID (ie. "C04PSNFH65Q")
      val postMessageJson =
        PostMessageRequest(
          channel = "test",
          response_type = "in_channel",
          blocks =
            listOf(
              Block(type = "section", text = Text(type = "mrkdwn", text = buildMrkdwn { append("this is a test.") }))
            ),
        )

      val response = slackApi.postMessage(postMessageJson).execute()

      response.checkSuccessful()

      return Response(
        body = "hello slack".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_OK,
        headers = Headers.headersOf(),
      )
    }
  }
}
