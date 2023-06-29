package slack

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.config.MiskConfig.RealSecret
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import `slack-api`.SlackApi
import `slack-api`.SlackClientInterceptor
import `slack-api`.SlackConfig

class PingSlackActionTest {
  private val server = MockWebServer()

  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private val slackConfig = SlackConfig(
    // ⚠️ DO NOT CHECK IN PRODUCTION KEYS!
    // The 'Bot User OAuth Token', here: https://api.slack.com/apps/A04NSLF2N11/oauth
    bearer_token = RealSecret("xoxb-1234567890123-4567890abcdef-FakeSecretFakeSecretFake"),

    // ⚠️ DO NOT CHECK IN PRODUCTION KEYS!
    // The 'Signing Secret', here: https://api.slack.com/apps/A04NSLF2N11/general
    signing_secret = RealSecret("abcdef0123456789abcdef0123456789")
  )

  private val pingSlackAction = PingSlackAction(
    slackApi = Retrofit.Builder()
      .client(
        OkHttpClient.Builder()
          .addInterceptor(SlackClientInterceptor(slackConfig))
          .build())
      .baseUrl(server.url("https://slack.com/"))
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(SlackApi::class.java),
    moshi = moshi,
  )

  /**
   * This is a facet for manual testing.
   */
  @Test
  @Disabled
  fun test() {
    val pingSlack = pingSlackAction.pingSlack()
    println(pingSlack)
  }
}
