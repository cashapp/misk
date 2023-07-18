package misk.slack.webapi

import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.PostMessageResponse

interface SlackClient {
  fun postMessage(
    request: PostMessageRequest,
  ): PostMessageResponse

  fun postConfirmation(
    url: String,
    request: PostMessageRequest,
  ): PostMessageResponse
}
