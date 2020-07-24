package misk.slack

import misk.logging.getLogger
import javax.inject.Inject

class RealSlackClient @Inject constructor(
  private val slackWebHookApi: SlackWebhookApi,
  private val config: SlackConfig
) : SlackClient() {
  /**
   * Post a message as the specified bot username and icon emoji in the channel.
   * If no channel is provided, the default channel configured by the service is used.
   * Does not throw on IO exceptions.
   */
  override fun postMessage(
    username: String,
    iconEmoji: String,
    message: String,
    channel: String?
  ): SlackWebhookResponse? {
    val resolvedChannel = channel ?: config.default_channel
    if (resolvedChannel == null) {
      logger.info("No default Slack channel configured, message not sent!")
      return null
    }
    val request = SlackWebhookRequest(
        channel = resolvedChannel,
        username = username,
        text = message,
        icon_emoji = iconEmoji
    )
    return try {
      val response = postMessage(request)
      if (response != SlackWebhookResponse.ok) {
        logger.warn("Error response posting message to Slack: %s", response)
      }
      response
    } catch (e: Exception) {
      logger.warn("Exception posting message to Slack", e)
      null
    }
  }

  private fun postMessage(request: SlackWebhookRequest): SlackWebhookResponse {
    val response = slackWebHookApi.post(config.webhook_path.value, request).execute()
    if (response.code() == 200) {
      return SlackWebhookResponse.ok
    }
    if (response.code() == 500) {
      return SlackWebhookResponse.valueOf(response.errorBody()!!.string())
    }
    throw IllegalStateException(
        "Unexpected HTTP response posting message to slack: " + response.code())
  }

  companion object {
    val logger = getLogger<RealSlackClient>()
  }
}
