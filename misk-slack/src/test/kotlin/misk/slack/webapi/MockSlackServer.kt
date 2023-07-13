package misk.slack.webapi

import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import misk.slack.webapi.helpers.PostMessage
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrap [MockWebServer] to pretend its a Slack server.
 */
@Singleton
class MockSlackServer @Inject constructor(
  moshi: Moshi,
) : AbstractIdleService() {
  val server = MockWebServer()

  private val jsonAdapter = moshi.adapter(PostMessage::class.java)
  override fun startUp() {
    server.start(60992)
  }

  override fun shutDown() {
    server.shutdown()
  }

  /** [SlackApi.postMessage] and [SlackApi.postConfirmation] return this */

  fun enqueueMessageResponse(postMessageJson: PostMessage) {
    server.enqueue(
      MockResponse()
        .setBody(jsonAdapter.toJson(postMessageJson))
    )
  }
}
