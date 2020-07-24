package misk.slack

import javax.inject.Inject

/** Dummy client that does nothing if SlackModule is not installed .*/
open class SlackClient @Inject constructor() {
  /**
   * Post a message as the specified bot username and icon emoji in the channel.
   * If no channel is provided, the default channel configured by the service is used.
   * If the service has not configured a slack module, this method is a no-op.
   * Does not throw on IO exceptions.
   */
  open fun postMessage(
    username: String,
    iconEmoji: String,
    message: String,
    channel: String? = null
  ): SlackWebhookResponse? {
    return null
  }
}
