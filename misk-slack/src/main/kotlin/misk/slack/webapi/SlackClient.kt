package misk.slack.webapi

import misk.slack.webapi.helpers.PostMessage
import misk.slack.webapi.helpers.PostMessageResponse

interface SlackClient {
  fun postMessage(
    request: PostMessage,
  ): PostMessageResponse

  fun postConfirmation(
    url: String,
    request: PostMessage,
  ): PostMessageResponse
}
