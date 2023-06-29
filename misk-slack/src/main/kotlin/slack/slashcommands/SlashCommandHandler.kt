package slack.slashcommands

import slack.BlockJson
import slack.PostMessageJson
import slack.SlackApi
import slack.SlashCommandJson
import slack.TextJson
import slack.checkSuccessful

abstract class SlashCommandHandler constructor(
  open val slackApi: SlackApi
) {
  /** Returns true if [slashCommandJson] was handled. */
  abstract fun handle(slashCommandJson: SlashCommandJson) : Boolean

  open fun sendSlackConfirmation(
    channelId: String,
    text: String,
    responseUrl: String
  ) {

    val confirmationJson = PostMessageJson(
      channel = channelId,
      blocks = listOf(
        BlockJson(
          type = "section",
          text = TextJson(
            type = "mrkdwn",
            text = text,
          )
        )
      )
    )

    val response = slackApi.postConfirmation(
      responseUrl,
      confirmationJson
    ).execute()

    response.checkSuccessful()
  }
}
