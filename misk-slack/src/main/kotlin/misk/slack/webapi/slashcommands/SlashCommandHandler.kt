package misk.slack.webapi.slashcommands

import misk.slack.webapi.SlackApi
import misk.slack.webapi.checkSuccessful
import misk.slack.webapi.helpers.Block
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.SlashCommand
import misk.slack.webapi.helpers.Text

abstract class SlashCommandHandler constructor(open val slackApi: SlackApi) {
  /**
   * Business logic to handle the slash command sent from the user https://api.slack.com/interactivity/slash-commands
   * Returns true if [slashCommandJson] was handled.
   */
  abstract fun handle(slashCommandJson: SlashCommand): Boolean

  open fun sendSlackConfirmation(channelId: String, text: String, responseUrl: String) {

    val confirmationJson =
      PostMessageRequest(
        channel = channelId,
        blocks = listOf(Block(type = "section", text = Text(type = "mrkdwn", text = text))),
      )

    val response = slackApi.postConfirmation(responseUrl, confirmationJson).execute()

    response.checkSuccessful()
  }
}
