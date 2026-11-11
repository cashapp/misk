package misk.slack.webapi

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.slack.webapi.helpers.GetUserResponse
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.PostMessageResponse
import misk.slack.webapi.helpers.UserData
import misk.testing.FakeFixture

/**
 * In-memory [SlackClient] with invocation histories and queued outcomes for each method. Calls are recorded before
 * throwing queued errors. Errors take precedence without consuming responses.
 */
@Singleton
class FakeSlackClient @Inject constructor() : SlackClient, FakeFixture() {
  val messages by resettable { mutableListOf<PostMessageRequest>() }

  /** Responses to the next [postMessage] calls, in order; `ok = true` with no ts once exhausted. */
  val responses by resettable { ArrayDeque<PostMessageResponse>() }

  /** Thrown by the next [postMessage] calls, in order, after recording the message (a lost response). */
  val errors by resettable { ArrayDeque<Exception>() }

  val postConfirmationCalls by resettable { mutableListOf<PostConfirmationCall>() }
  val postConfirmationResponses by resettable { ArrayDeque<PostMessageResponse>() }
  val postConfirmationErrors by resettable { ArrayDeque<Exception>() }

  val getUserByEmailCalls by resettable { mutableListOf<String>() }
  val getUserByEmailResponses by resettable { ArrayDeque<GetUserResponse>() }
  val getUserByEmailErrors by resettable { ArrayDeque<Exception>() }

  val getUserByIdCalls by resettable { mutableListOf<String>() }
  val getUserByIdResponses by resettable { ArrayDeque<GetUserResponse>() }
  val getUserByIdErrors by resettable { ArrayDeque<Exception>() }

  data class PostConfirmationCall(val url: String, val request: PostMessageRequest)

  override fun getUserByEmail(email: String): GetUserResponse {
    getUserByEmailCalls.add(email)
    getUserByEmailErrors.removeFirstOrNull()?.let { throw it }
    return getUserByEmailResponses.removeFirstOrNull()
      ?: GetUserResponse(ok = true, user = UserData(id = "123", name = "test"))
  }

  override fun getUserById(userId: String): GetUserResponse {
    getUserByIdCalls.add(userId)
    getUserByIdErrors.removeFirstOrNull()?.let { throw it }
    return getUserByIdResponses.removeFirstOrNull()
      ?: GetUserResponse(ok = true, user = UserData(id = userId, name = "test"))
  }

  override fun postConfirmation(url: String, request: PostMessageRequest): PostMessageResponse {
    postConfirmationCalls.add(PostConfirmationCall(url, request))
    postConfirmationErrors.removeFirstOrNull()?.let { throw it }
    return postConfirmationResponses.removeFirstOrNull() ?: PostMessageResponse(ok = true)
  }

  override fun postMessage(request: PostMessageRequest): PostMessageResponse {
    messages.add(request)
    errors.removeFirstOrNull()?.let { throw it }
    return responses.removeFirstOrNull() ?: PostMessageResponse(ok = true)
  }
}
