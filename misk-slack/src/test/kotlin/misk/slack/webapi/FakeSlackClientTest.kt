package misk.slack.webapi

import com.google.inject.Guice
import com.google.inject.Key
import com.google.inject.TypeLiteral
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import misk.slack.webapi.helpers.Block
import misk.slack.webapi.helpers.GetUserResponse
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.PostMessageResponse
import misk.slack.webapi.helpers.Text
import misk.slack.webapi.helpers.UserData
import misk.testing.TestFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FakeSlackClientTest {
  private val client = FakeSlackClient()
  private val request =
    PostMessageRequest(
      channel = "C123",
      blocks = listOf(Block(type = "section", text = Text(type = "mrkdwn", text = "hello"))),
    )

  @Test
  fun `records messages and succeeds by default`() {
    val reply = request.copy(thread_ts = "123.456")

    assertThat(client.postMessage(request)).isEqualTo(PostMessageResponse(ok = true))
    assertThat(client.postMessage(reply)).isEqualTo(PostMessageResponse(ok = true))
    assertThat(client.messages).containsExactly(request, reply)
  }

  @Test
  fun `returns queued responses in order then defaults to success`() {
    val failure = PostMessageResponse(ok = false, error = "ratelimited")
    val success = PostMessageResponse(ok = true, channel = request.channel, ts = "123.456")
    client.responses.addAll(listOf(failure, success))

    assertThat(client.postMessage(request)).isEqualTo(failure)
    assertThat(client.postMessage(request)).isEqualTo(success)
    assertThat(client.postMessage(request)).isEqualTo(PostMessageResponse(ok = true))
    assertThat(client.messages).containsExactly(request, request, request)
  }

  @Test
  fun `records messages before throwing queued errors without consuming responses`() {
    val firstError = IOException("lost response")
    val secondError = IllegalStateException("unavailable")
    val response = PostMessageResponse(ok = true, ts = "123.456")
    client.errors.addAll(listOf(firstError, secondError))
    client.responses.add(response)

    assertThatThrownBy { client.postMessage(request) }.isSameAs(firstError)
    assertThatThrownBy { client.postMessage(request) }.isSameAs(secondError)
    assertThat(client.messages).containsExactly(request, request)
    assertThat(client.responses).containsExactly(response)
    assertThat(client.postMessage(request)).isEqualTo(response)
  }

  @Test
  fun `concurrent callers each consume a distinct queued response`() {
    val queued = (1..200).map { PostMessageResponse(ok = true, ts = "$it") }
    client.responses.addAll(queued)
    val executor = Executors.newFixedThreadPool(8)
    val start = CountDownLatch(1)

    val results =
      try {
        val futures =
          queued.map {
            executor.submit<PostMessageResponse> {
              start.await()
              client.postMessage(request)
            }
          }
        start.countDown()
        futures.map { it.get(10, TimeUnit.SECONDS) }
      } finally {
        executor.shutdownNow()
      }

    assertThat(results).containsExactlyInAnyOrderElementsOf(queued)
    assertThat(client.messages).hasSize(queued.size)
    assertThat(client.responses).isEmpty()
  }

  @Test
  fun `reset clears all invocations and pending outcomes`() {
    client.postMessage(request)
    client.responses.add(PostMessageResponse(ok = false, error = "ratelimited"))
    client.errors.add(IOException("lost response"))
    client.postConfirmation("https://example.com/response", request)
    client.postConfirmationResponses.add(PostMessageResponse(ok = false, error = "ratelimited"))
    client.postConfirmationErrors.add(IOException("lost confirmation"))
    client.getUserByEmail("test@example.com")
    client.getUserByEmailResponses.add(GetUserResponse(ok = false, error = "users_not_found"))
    client.getUserByEmailErrors.add(IOException("email lookup failed"))
    client.getUserById("U456")
    client.getUserByIdResponses.add(GetUserResponse(ok = false, error = "user_not_found"))
    client.getUserByIdErrors.add(IOException("ID lookup failed"))

    client.reset()

    assertThat(client.messages).isEmpty()
    assertThat(client.responses).isEmpty()
    assertThat(client.errors).isEmpty()
    assertThat(client.postConfirmationCalls).isEmpty()
    assertThat(client.postConfirmationResponses).isEmpty()
    assertThat(client.postConfirmationErrors).isEmpty()
    assertThat(client.getUserByEmailCalls).isEmpty()
    assertThat(client.getUserByEmailResponses).isEmpty()
    assertThat(client.getUserByEmailErrors).isEmpty()
    assertThat(client.getUserByIdCalls).isEmpty()
    assertThat(client.getUserByIdResponses).isEmpty()
    assertThat(client.getUserByIdErrors).isEmpty()
    assertThat(client.postMessage(request)).isEqualTo(PostMessageResponse(ok = true))
  }

  @Test
  fun `confirmation calls record both arguments and use independent queued outcomes`() {
    val firstError = IOException("lost confirmation")
    val secondError = IllegalStateException("unavailable")
    val failure = PostMessageResponse(ok = false, error = "ratelimited")
    val success = PostMessageResponse(ok = true, ts = "123.456")
    client.postConfirmationErrors.addAll(listOf(firstError, secondError))
    client.postConfirmationResponses.addAll(listOf(failure, success))
    val url = "https://example.com/response"
    val reply = request.copy(thread_ts = "123.456")

    assertThatThrownBy { client.postConfirmation(url, request) }.isSameAs(firstError)
    assertThatThrownBy { client.postConfirmation(url, reply) }.isSameAs(secondError)
    assertThat(client.postConfirmationResponses).containsExactly(failure, success)
    assertThat(client.postConfirmation(url, request)).isEqualTo(failure)
    assertThat(client.postConfirmation(url, reply)).isEqualTo(success)
    assertThat(client.postConfirmation("$url/next", request)).isEqualTo(PostMessageResponse(ok = true))
    assertThat(client.postConfirmationCalls)
      .containsExactly(
        FakeSlackClient.PostConfirmationCall(url, request),
        FakeSlackClient.PostConfirmationCall(url, reply),
        FakeSlackClient.PostConfirmationCall(url, request),
        FakeSlackClient.PostConfirmationCall(url, reply),
        FakeSlackClient.PostConfirmationCall("$url/next", request),
      )
    assertThat(client.messages).isEmpty()
    assertThat(client.postMessage(request)).isEqualTo(PostMessageResponse(ok = true))
  }

  @Test
  fun `email lookups record arguments and use queued outcomes`() {
    val firstError = IOException("email lookup failed")
    val secondError = IllegalStateException("unavailable")
    val failure = GetUserResponse(ok = false, error = "users_not_found")
    val success = GetUserResponse(ok = true, user = UserData(id = "U456"))
    client.getUserByEmailErrors.addAll(listOf(firstError, secondError))
    client.getUserByEmailResponses.addAll(listOf(failure, success))

    assertThatThrownBy { client.getUserByEmail("first@example.com") }.isSameAs(firstError)
    assertThatThrownBy { client.getUserByEmail("second@example.com") }.isSameAs(secondError)
    assertThat(client.getUserByEmailResponses).containsExactly(failure, success)
    assertThat(client.getUserByEmail("third@example.com")).isEqualTo(failure)
    assertThat(client.getUserByEmail("fourth@example.com")).isEqualTo(success)
    assertThat(client.getUserByEmail("fifth@example.com"))
      .isEqualTo(GetUserResponse(ok = true, user = UserData(id = "123", name = "test")))
    assertThat(client.getUserByEmailCalls)
      .containsExactly(
        "first@example.com",
        "second@example.com",
        "third@example.com",
        "fourth@example.com",
        "fifth@example.com",
      )
    assertThat(client.getUserByIdCalls).isEmpty()
  }

  @Test
  fun `ID lookups record arguments and use queued outcomes`() {
    val firstError = IOException("ID lookup failed")
    val secondError = IllegalStateException("unavailable")
    val failure = GetUserResponse(ok = false, error = "user_not_found")
    val success = GetUserResponse(ok = true, user = UserData(id = "U456"))
    client.getUserByIdErrors.addAll(listOf(firstError, secondError))
    client.getUserByIdResponses.addAll(listOf(failure, success))

    assertThatThrownBy { client.getUserById("U1") }.isSameAs(firstError)
    assertThatThrownBy { client.getUserById("U2") }.isSameAs(secondError)
    assertThat(client.getUserByIdResponses).containsExactly(failure, success)
    assertThat(client.getUserById("U3")).isEqualTo(failure)
    assertThat(client.getUserById("U4")).isEqualTo(success)
    assertThat(client.getUserById("U5"))
      .isEqualTo(GetUserResponse(ok = true, user = UserData(id = "U5", name = "test")))
    assertThat(client.getUserByIdCalls).containsExactly("U1", "U2", "U3", "U4", "U5")
    assertThat(client.getUserByEmailCalls).isEmpty()
  }

  @Test
  fun `user lookups and confirmations succeed by default`() {
    assertThat(client.getUserByEmail("test@example.com"))
      .isEqualTo(GetUserResponse(ok = true, user = UserData(id = "123", name = "test")))
    assertThat(client.getUserById("U456"))
      .isEqualTo(GetUserResponse(ok = true, user = UserData(id = "U456", name = "test")))
    assertThat(client.postConfirmation("https://example.com/response", request))
      .isEqualTo(PostMessageResponse(ok = true))
    assertThat(client.messages).isEmpty()
  }

  @Test
  fun `module shares one fake across client and reset bindings`() {
    val injector = Guice.createInjector(FakeSlackClientModule())
    val fake = injector.getInstance(FakeSlackClient::class.java)
    val fixtures = injector.getInstance(Key.get(object : TypeLiteral<Set<TestFixture>>() {}))

    assertThat(injector.getInstance(SlackClient::class.java)).isSameAs(fake)
    assertThat(fixtures).containsExactly(fake)
    fake.postMessage(request)
    fixtures.forEach { it.reset() }
    assertThat(fake.messages).isEmpty()
  }
}
