package misk.slack.webapi

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import misk.slack.webapi.helpers.GetUserResponse
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.PostMessageResponse
import misk.slack.webapi.helpers.UserData
import misk.testing.FakeFixture

/**
 * In-memory [SlackClient] with invocation histories and queued outcomes for each method. Calls are recorded before
 * throwing queued errors. Errors take precedence without consuming responses.
 *
 * Safe to call from multiple threads: each queued outcome is consumed by exactly one call.
 */
@Singleton
class FakeSlackClient @Inject constructor() : SlackClient, FakeFixture() {
  val messages: MutableList<PostMessageRequest> by resettable { CopyOnWriteArrayList<PostMessageRequest>() }

  /** Responses to the next [postMessage] calls, in order; `ok = true` with no ts once exhausted. */
  val responses: Queue<PostMessageResponse> by resettable { ConcurrentLinkedQueue<PostMessageResponse>() }

  /** Thrown by the next [postMessage] calls, in order, after recording the message (a lost response). */
  val errors: Queue<Exception> by resettable { ConcurrentLinkedQueue<Exception>() }

  val postConfirmationCalls: MutableList<PostConfirmationCall> by resettable {
    CopyOnWriteArrayList<PostConfirmationCall>()
  }
  val postConfirmationResponses: Queue<PostMessageResponse> by resettable {
    ConcurrentLinkedQueue<PostMessageResponse>()
  }
  val postConfirmationErrors: Queue<Exception> by resettable { ConcurrentLinkedQueue<Exception>() }

  val getUserByEmailCalls: MutableList<String> by resettable { CopyOnWriteArrayList<String>() }
  val getUserByEmailResponses: Queue<GetUserResponse> by resettable { ConcurrentLinkedQueue<GetUserResponse>() }
  val getUserByEmailErrors: Queue<Exception> by resettable { ConcurrentLinkedQueue<Exception>() }

  val getUserByIdCalls: MutableList<String> by resettable { CopyOnWriteArrayList<String>() }
  val getUserByIdResponses: Queue<GetUserResponse> by resettable { ConcurrentLinkedQueue<GetUserResponse>() }
  val getUserByIdErrors: Queue<Exception> by resettable { ConcurrentLinkedQueue<Exception>() }

  data class PostConfirmationCall(val url: String, val request: PostMessageRequest)

  override fun getUserByEmail(email: String): GetUserResponse {
    getUserByEmailCalls.add(email)
    getUserByEmailErrors.poll()?.let { throw it }
    return getUserByEmailResponses.poll() ?: GetUserResponse(ok = true, user = UserData(id = "123", name = "test"))
  }

  override fun getUserById(userId: String): GetUserResponse {
    getUserByIdCalls.add(userId)
    getUserByIdErrors.poll()?.let { throw it }
    return getUserByIdResponses.poll() ?: GetUserResponse(ok = true, user = UserData(id = userId, name = "test"))
  }

  override fun postConfirmation(url: String, request: PostMessageRequest): PostMessageResponse {
    postConfirmationCalls.add(PostConfirmationCall(url, request))
    postConfirmationErrors.poll()?.let { throw it }
    return postConfirmationResponses.poll() ?: PostMessageResponse(ok = true)
  }

  override fun postMessage(request: PostMessageRequest): PostMessageResponse {
    messages.add(request)
    errors.poll()?.let { throw it }
    return responses.poll() ?: PostMessageResponse(ok = true)
  }
}
