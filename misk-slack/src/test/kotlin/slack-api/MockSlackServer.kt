package com.squareup.cash.treelot.launchdarkly

import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import `slack-api`.PostMessageJson

/**
 * Wrap [MockWebServer] to pretend its a Slack server.
 */
@Singleton
class MockSlackServer @Inject constructor(
  moshi: Moshi
) : AbstractIdleService() {
  val server = MockWebServer()

  private val jsonAdapter = moshi.adapter(PostMessageJson::class.java)
  override fun startUp() {
    server.start(8912)
  }

  override fun shutDown() {
    server.shutdown()
  }

  /** [SlackApi.postMessage] and [SlackApi.postConfirmation] return this */

  fun enqueueMessageResponse(postMessageJson: PostMessageJson) {
    server.enqueue(MockResponse()
      .setBody(jsonAdapter.toJson(postMessageJson)))
  }
}
