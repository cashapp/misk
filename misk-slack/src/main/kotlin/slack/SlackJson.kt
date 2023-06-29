@file:Suppress("PropertyName") // We use snake_case to match Slack's JSON.

package slack

/**
 * Message posted to /api/chat.postMessage
 *
 * https://api.slack.com/methods/chat.postMessage
 */
data class PostMessageJson(
  val channel: String,
  val response_type: String? = "in_channel",
  val blocks: List<Any>,
)

/**
 * An envelope that contains text or elements.
 *
 * https://api.slack.com/reference/block-kit/blocks
 */
data class BlockJson(
  val type: String,
  val replace_original: Boolean? = null,
  val block_id: String? = null,
  val text: TextJson? = null,
  val accessory: ButtonLinkAndValueJson? = null,
  val elements: List<ButtonLinkAndValueJson>? = null,
)

/** https://api.slack.com/reference/block-kit/composition-objects#text */
data class TextJson(
  val type: String,
  val text: String? = null,
  /** This must be null if [type] is "mrkdwn". */
  val emoji: Boolean? = null,
)

/** https://api.slack.com/reference/block-kit/block-elements#button */
data class ButtonLinkAndValueJson(
  val type: String,
  val text: TextJson,
  val value: String? = null,
  val url: String? = null,
  val action_id: String? = null
)

/**
 * Message received from slack after posting a message
 *
 * https://api.slack.com/methods/chat.postMessage
 */
data class PostMessageResponseJson(
  val ok: Boolean,
  val error: String? = null,
  val channel: String? = null,
  val ts: String? = null,
  val message: MessageJson? = null,
)

data class MessageJson(
  val bot_id: String,
  val type: String,
  val text: String,
  val user: String,
  val ts: String,
  val app_id: String,
  val blocks: List<BlockJson>,
  val team: String
)

/**
 * Message received from Slack upon button press.
 *
 * https://api.slack.com/reference/interaction-payloads/block-actions
 */
data class ButtonPressJson(
  val type: String,
  val user: UserJson,
  val api_app_id: String,
  val token: String,
  val container: ContainerJson,
  val trigger_id: String,
  val team: TeamJson,
  val enterprise: IdJson,
  val is_enterprise_install: Boolean,
  val channel: IdJson,
  val message: MessageJson,
  val state: ValuesJson,
  val response_url: String,
  val actions: List<ActionsJson>
)

data class UserJson(
  val id: String,
  val username: String,
  val name: String,
  val team_id: String
)

data class ContainerJson(
  val type: String,
  val message_ts: String,
  val channel_id: String,
  val is_ephemeral: Boolean,
)

data class TeamJson(
  val id: String,
  val domain: String,
  val enterprise_id: String,
  val enterprise_name: String
)

data class IdJson(
  val id: String,
  val name: String
)

data class ValuesJson(
  val text: String?
)

data class ActionsJson(
  val type: String,
  val block_id: String,
  val action_id: String,
  val text: TextJson,
  val value: String,
  val action_ts: String
)

/**
 * Payload received from Slack when user invokes slash command
 */
data class SlashCommandJson(
  val command: String,
  val text: String,
  val response_url: String,
  val trigger_id: String,
  val user_id: String,
  val user_name: String,
  val channel_id: String,
  val api_app_id: String
)

/**
 * Response sent back to Slack while slash commands are being handled.
 */
data class SlashInteractionResponseJson(
  val response_type: String? = "in_channel",
  val text: String
)
