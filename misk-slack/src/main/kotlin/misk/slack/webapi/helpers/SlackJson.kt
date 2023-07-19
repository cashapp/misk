package misk.slack.webapi.helpers

/**
 * Message posted to /api/chat.postMessage
 *
 * https://api.slack.com/methods/chat.postMessage
 */
data class PostMessageRequest @JvmOverloads constructor(
  val channel: String,
  val response_type: String? = "in_channel",
  val blocks: List<Any>,
)

/**
 * An envelope that contains text or elements.
 *
 * https://api.slack.com/reference/block-kit/blocks
 */
data class Block @JvmOverloads constructor(
  val type: String,
  val replace_original: Boolean? = null,
  val block_id: String? = null,
  val text: Text? = null,
  val accessory: ButtonLinkAndValue? = null,
  val elements: List<ButtonLinkAndValue>? = null,
)

/** https://api.slack.com/reference/block-kit/composition-objects#text */
data class Text @JvmOverloads constructor(
  val type: String,
  val text: String? = null,
  /** This must be null if [type] is "mrkdwn". */
  val emoji: Boolean? = null,
)

/** https://api.slack.com/reference/block-kit/block-elements#button */
data class ButtonLinkAndValue @JvmOverloads constructor(
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
data class PostMessageResponse @JvmOverloads constructor(
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
data class SlashInteractionResponse @JvmOverloads constructor(
  val response_type: String? = "in_channel",
  val text: String,
)

/**
 * An object containing info related to an Enterprise Grid user.
 *
 * https://api.slack.com/enterprise/grid
 */
data class EnterpriseUser @JvmOverloads constructor(
  /**
   * A unique ID for the Enterprise Grid organization this user belongs to.
   */
  val enterprise_id: String? = null,
  /**
   * A display name for the Enterprise Grid organization.
   */
  val enterprise_name: String? = null,
  /**
   * This user's ID - some Grid users have a kind of dual identity — a local, workspace-centric
   * user ID as well as a Grid-wise user ID, called the Enterprise user ID. In most cases these
   * IDs can be used interchangeably, but when it is provided, we strongly recommend using this
   * Enterprise user id over the root level user id field.
   */
  val id: String? = null,
  /**
   * Indicates whether the user is an Admin of the Enterprise Grid organization.
   */
  val is_admin: Boolean? = null,
  /**
   * Indicates whether the user is an Owner of the Enterprise Grid organization.
   */
  val is_owner: Boolean? = null,
  /**
   * An array of workspace IDs that are in the Enterprise Grid organization.
   */
  val teams: Array<String>? = null
)

/**
 * The following fields are the default fields of a user's workspace profile.
 *
 * https://api.slack.com/types/user#profile
 */
data class UserProfileFields @JvmOverloads constructor(
  /**
   * The display name the user has chosen to identify themselves by in their workspace profile.
   */
  val display_name: String?,
  /**
   * A valid email address. It cannot have spaces, or be in use by another member of the same team.
   * It must have an @ and a domain.
   * Changing a user's email address will send an email to both the old and new addresses,
   * and also post a slackbot message to the user informing them of the change.
   * You cannot update your own email using this method.
   * This field can only be changed by admins for users on paid teams.
   */
  val email: String?,
  /**
   * The user's first name. The name "slackbot" cannot be used.
   * Updating first_name will update the first name within real_name.
   */
  val first_name: String?,
  /**
   * The user's last name. The name "slackbot" cannot be used.
   * Updating last_name will update the second name within real_name.
   */
  val last_name: String?,
  /**
   * The user's phone number, in any format.
   */
  val phone: String?,
  /**
   * The user's pronouns.
   */
  val pronouns: String?,
  /**
   * The user's first and last name.
   * Updating this field will update first_name and last_name.
   * If only one name is provided, the value of last_name will be cleared.
   */
  val real_name: String?,
  /**
   * The date the person joined the organization.
   * Only available if Slack Atlas is enabled.
   */
  val start_date: String?,
  /**
   * The user's title.
   */
  val title: String?
)

/**
 * A user object contains information about a Slack workspace user.
 *
 * https://api.slack.com/types/user
 */
data class UserProfile @JvmOverloads constructor(
  /**
   * The hash identifier for the user's avatar image.
   */
  val avatar_hash: String? = null,
  /**
   * The display name the user has chosen to identify themselves by in their workspace profile.
   * Do not use this field as a unique identifier for a user, as it may change at any time.
   * Instead, use id and team_id in concert.
   */
  val display_name: String? = null,
  /**
   * The display_name field, but with any non-Latin characters filtered out.
   */
  val display_name_normalized: String? = null,
  /**
   * A valid email address for the user. It cannot have spaces and must have an @ and a domain.
   * It cannot be in use by another member of the same team. Changing a user's email address will
   * send an email to both the old and new addresses and post a slackbot to the user informing
   * them of the change. This field can only be changed by admins for users on paid teams.
   */
  val email: String? = null,
  /**
   * All the custom profile fields for the user.
   */
  val fields: UserProfileFields? = null,
  /**
   * The user's first name. The name "slackbot" cannot be used.
   * Updating first_name will update the first name within real_name.
   */
  val first_name: String? = null,
  /**
   * URL pointing to a 24x24 pixel image representing the user's profile picture.
   */
  val image_24: String? = null,
  /**
   * URL pointing to a 32x32 pixel image representing the user's profile picture.
   */
  val image_32: String? = null,
  /**
   * URL pointing to a 48x48 pixel image representing the user's profile picture.
   */
  val image_48: String? = null,
  /**
   * URL pointing to a 72x72 pixel image representing the user's profile picture.
   */
  val image_72: String? = null,
  /**
   * URL pointing to a 192x192 pixel image representing the user's profile picture.
   */
  val image_192: String? = null,
  /**
   * URL pointing to a 512x512 pixel image representing the user's profile picture.
   */
  val image_512: String? = null,
  /**
   * The user's last name. The name "slackbot" cannot be used.
   * Updating last_name will update the second name within real_name.
   */
  val last_name: String? = null,
  /**
   * The user's phone number, in any format.
   */
  val phone: String? = null,
  /**
   * The pronouns the user prefers to be addressed by.
   */
  val pronouns: String? = null,
  /**
   * The user's first and last name. Updating this field will update first_name and last_name.
   * If only one name is provided, the value of last_name will be cleared.
   */
  val real_name: String? = null,
  /**
   * The real_name field, but with any non-Latin characters filtered out.
   */
  val real_name_normalized: String? = null,
  /**
   * A shadow from a bygone era. It will always be an empty string and cannot be set otherwise.
   */
  val skype: String? = null,
  /**
   * The date the person joined the organization. Only available if Slack Atlas is enabled.
   */
  val start_date: String? = null,
  /**
   * The displayed emoji that is enabled for the Slack team, such as ":train:".
   */
  val status_emoji: String? = null,
  /**
   * The Unix timestamp of when the status will expire.
   * Providing 0 or omitting this field results in a custom status that will not expire.
   */
  val status_expiration: Int? = null,
  /**
   * The displayed text of up to 100 characters. We strongly encourage brevity.
   */
  val status_text: String? = null,
  /**
   * The ID of the team the user is on.
   */
  val team: String? = null,
  /**
   * The user's title.
   */
  val title: String? = null
)

/**
 * A user object contains information about a Slack workspace user. The composition of user
 * objects can vary greatly depending on the API being used, or the context of each Slack
 * workspace. Data that has not been supplied may not be present at all, may be null, or may
 * contain an empty string.
 *
 * https://api.slack.com/types/user
 */
data class UserData @JvmOverloads constructor(
  /**
   * Indicates that a bot user is set to be constantly active in presence status.
   */
  val always_active: Boolean? = null,
  /**
   * Used in some clients to display a special username color.
   */
  val color: String? = null,
  /**
   * This user has been deactivated when the value of this field is true.
   * Otherwise, the value is false, or the field may not appear at all.
   */
  val deleted: Boolean? = null,
  /**
   * An object containing info related to an Enterprise Grid user.
   */
  val enterprise_user: EnterpriseUser? = null,
  /**
   * Describes whether two-factor authentication is enabled for this user.
   * Only visible if the user executing the call is an admin.
   */
  val has_2fa: Boolean? = null,
  /**
   * Identifier for this workspace user. It is unique to the workspace containing the user. Use
   * this field together with team_id as a unique key when storing related data or when specifying
   * the user in API requests. We recommend considering the format of the string to be an opaque
   * value, and not to rely on a particular structure.
   */
  val id: String? = null,
  /**
   * Indicates whether the user is an Admin of the current workspace.
   */
  val is_admin: Boolean? = null,
  /**
   * Indicates whether the user is an authorized user of the calling app.
   */
  val is_app_user: Boolean? = null,
  /**
   * Indicates whether the user is actually a bot user. Bleep bloop.
   * Note that Slackbot is special, so is_bot will be false for it.
   */
  val is_bot: Boolean? = null,
  /**
   * Indicates whether the user email has been confirmed.
   */
  val is_email_confirmed: Boolean? = null,
  /**
   * Only present (and always true) when a user has been invited but has not yet signed in.
   * Once the user signs in, this field is no longer present.
   */
  val is_invited_user: Boolean? = null,
  /**
   * Indicates whether the user is an Owner of the current workspace.
   */
  val is_owner: Boolean? = null,
  /**
   * Indicates whether the user is the Primary Owner of the current workspace.
   */
  val is_primary_owner: Boolean? = null,
  /**
   * Indicates whether the user is a guest user. Use in combination with the is_ultra_restricted
   * field to check if the user is a single-channel guest user.
   */
  val is_restricted: Boolean? = null,
  /**
   * If true, this user belongs to a different workspace than the one associated with your app's
   * token, and isn't in any shared channels visible to your app. If false (or this field is not
   * present), the user is either from the same workspace as associated with your app's token, or
   * they are from a different workspace, but are in a shared channel that your app has access to.
   * Read our shared channels docs for more detail.
   */
  val is_stranger: Boolean? = null,
  /**
   * Indicates whether the user is a single-channel guest.
   */
  val is_ultra_restricted: Boolean? = null,
  /**
   * Contains an IETF language code that represents this user's chosen display language for Slack
   * clients. Useful for localizing your apps.
   */
  val locale: String? = null,
  /**
   * Don't use this. It once indicated the preferred username for a user,
   * but that behavior has fundamentally changed since.
   */
  val name: String? = null,
  /**
   * The profile object contains the default fields of a user's workspace profile.
   * A user's custom profile fields may be discovered using users.profile.get.
   */
  val profile: UserProfile? = null,
  /**
   * The user's first and last name. Updating this field will update first_name and last_name.
   * If only one name is provided, the value of last_name will be cleared.
   */
  val real_name: String? = null,
  /**
   * The ID of the team the user is on.
   */
  val team_id: String? = null,
  /**
   * Indicates the type of two-factor authentication in use.
   * Only present if has_2fa is true. The value will be either app or sms.
   */
  val two_factor_type: String? = null,
  /**
   * A human-readable string for the geographic timezone-related region
   * this user has specified in their account.
   */
  val tz: String? = null,
  /**
   * Describes the commonly used name of the tz timezone.
   */
  val tz_label: String? = null,
  /**
   * Indicates the number of seconds to offset UTC time by for this user's tz.
   * Changes silently if changed due to daylight savings.
   */
  val tz_offset: Int? = null,
  /**
   * A Unix timestamp indicating when the user object was last updated.
   */
  val updated: Int? = null
)

/**
 * Message received from slack after posting a message
 *
 * https://api.slack.com/methods/chat.postMessage
 */
data class GetUserResponse @JvmOverloads constructor(
  val ok: Boolean,
  val error: String? = null,
  val user: UserData? = null
)
