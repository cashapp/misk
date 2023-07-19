package misk.slack.webapi

import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.UserData
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
  private val server = MockWebServer()

  private val messageJsonAdapter = moshi.adapter(PostMessageRequest::class.java)
  private val userJsonAdapter = moshi.adapter(UserData::class.java)

  override fun startUp() {
    server.start()
  }

  override fun shutDown() {
    server.shutdown()
  }

  /** [SlackApi.postMessage] and [SlackApi.postConfirmation] return this */
  fun enqueueMessageResponse(postMessageJson: PostMessageRequest) {
    server.enqueue(
      MockResponse()
        .setBody(messageJsonAdapter.toJson(postMessageJson))
    )
  }

  /** [SlackApi.getUserByEmail] returns this */
  fun enqueueUserResponse(userData: UserData) {
    server.enqueue(
      MockResponse()
        .setBody(userJsonAdapter.toJson(userData))
    )
  }
}
