package `slack-api`

interface SlackClient {
  fun postMessage(
    request: PostMessageJson,
  ): PostMessageResponseJson

  fun postConfirmation(
    url: String,
    request: PostMessageJson,
  ): PostMessageResponseJson
}
