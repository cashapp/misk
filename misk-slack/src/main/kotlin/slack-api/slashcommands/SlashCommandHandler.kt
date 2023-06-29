package `slack-api`.slashcommands

import `slack-api`.BlockJson
import `slack-api`.PostMessageJson
import `slack-api`.SlackApi
import `slack-api`.SlashCommandJson
import `slack-api`.TextJson
import `slack-api`.checkSuccessful

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
