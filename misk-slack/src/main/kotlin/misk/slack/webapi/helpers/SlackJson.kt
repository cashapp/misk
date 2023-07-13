package misk.slack.webapi.helpers

/**
 * Message posted to /api/chat.postMessage
 *
 * https://api.slack.com/methods/chat.postMessage
 */
data class PostMessage(
  val channel: String,
  val response_type: String? = "in_channel",
  val blocks: List<Any>,
)

/**
 * An envelope that contains text or elements.
 *
 * https://api.slack.com/reference/block-kit/blocks
 */
data class Block(
  val type: String,
  val replace_original: Boolean? = null,
  val block_id: String? = null,
  val text: Text? = null,
  val accessory: ButtonLinkAndValue? = null,
  val elements: List<ButtonLinkAndValue>? = null,
)

/** https://api.slack.com/reference/block-kit/composition-objects#text */
data class Text(
  val type: String,
  val text: String? = null,
  /** This must be null if [type] is "mrkdwn". */
  val emoji: Boolean? = null,
)

/** https://api.slack.com/reference/block-kit/block-elements#button */
data class ButtonLinkAndValue(
  val type: String,
  val text: Text,
  val value: String? = null,
  val url: String? = null,
  val action_id: String? = null,
)

/**
 * Message received from slack after posting a message
 *
 * https://api.slack.com/methods/chat.postMessage
 */
data class PostMessageResponse(
  val ok: Boolean,
  val error: String? = null,
  val channel: String? = null,
  val ts: String? = null,
  val message: Message? = null,
)

data class Message(
  val bot_id: String,
  val type: String,
  val text: String,
  val user: String,
  val ts: String,
  val app_id: String,
  val blocks: List<Block>,
  val team: String,
)

/**
 * Message received from Slack upon button press.
 *
 * https://api.slack.com/reference/interaction-payloads/block-actions
 */
data class ButtonPress(
  val type: String,
  val user: User,
  val api_app_id: String,
  val token: String,
  val container: Container,
  val trigger_id: String,
  val team: Team,
  val enterprise: Id,
  val is_enterprise_install: Boolean,
  val channel: Id,
  val message: Message,
  val state: Values,
  val response_url: String,
  val actions: List<Actions>,
)

data class User(
  val id: String,
  val username: String,
  val name: String,
  val team_id: String,
)

data class Container(
  val type: String,
  val message_ts: String,
  val channel_id: String,
  val is_ephemeral: Boolean,
)

data class Team(
  val id: String,
  val domain: String,
  val enterprise_id: String,
  val enterprise_name: String,
)

data class Id(
  val id: String,
  val name: String,
)

data class Values(
  val text: String?,
)

data class Actions(
  val type: String,
  val block_id: String,
  val action_id: String,
  val text: Text,
  val value: String,
  val action_ts: String,
)

/**
 * Payload received from Slack when user invokes slash command
 */
data class SlashCommand(
  val command: String,
  val text: String,
  val response_url: String,
  val trigger_id: String,
  val user_id: String,
  val user_name: String,
  val channel_id: String,
  val api_app_id: String,
)

/**
 * Response sent back to Slack while slash commands are being handled.
 */
data class SlashInteractionResponse(
  val response_type: String? = "in_channel",
  val text: String,
)
