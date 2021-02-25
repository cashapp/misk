package misk.cloud.gcp.testing

import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpResponseException
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.testing.http.MockHttpContent
import misk.cloud.gcp.testing.FakeHttpRouter.Companion.respondWithError
import misk.cloud.gcp.testing.FakeHttpRouter.Companion.respondWithText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertFailsWith

internal class FakeHttpRouterTest {
  private val transport = FakeHttpRouter {
    when (it.url) {
      "https://something.com/first" -> respondWithText("first!")

      "https://something.com/second" ->
        when (it.jsonContent<Body>()?.message) {
          "HELLO", "BYE" -> respondWithText("${it.jsonContent<Body>()?.message} second!")
          else -> respondWithError(401)
        }

      else -> respondWithError(404)
    }
  }

  @Test
  fun matchOnUrlAndBody() {
    val response = transport.createRequestFactory()
      .buildGetRequest(GenericUrl("https://something.com/second"))
      .setContent(
        MockHttpContent().setContent(JacksonFactory().toByteArray(Body("BYE")))
      )
      .execute()
    assertThat(response.parseAsString()).isEqualTo("BYE second!")
  }

  @Test
  fun matchOnUrl() {
    assertThat(
      assertFailsWith<HttpResponseException> {
        transport.createRequestFactory()
          .buildGetRequest(GenericUrl("https://something.com/second"))
          .setContent(
            MockHttpContent().setContent(JacksonFactory().toByteArray(Body("UNKNOWN")))
          ).execute()
      }.statusCode
    ).isEqualTo(401)
  }

  @Test
  fun noMatch() {
    assertThat(
      assertFailsWith<HttpResponseException> {
        transport.createRequestFactory()
          .buildGetRequest(GenericUrl("https://something.com/unknown"))
          .execute()
      }.statusCode
    ).isEqualTo(404)
  }

  @Test
  fun jsonContent() {
    val request = FakeHttpRequest("GET", "https://something.com") {
      throw IllegalArgumentException("should not execute")
    }
    request.setStreamingContent {
      it.write(JacksonFactory().toByteArray(Body("BYE")))
    }

    assertThat(request.jsonContent<Body>()?.message).isEqualTo("BYE")
    assertThat(request.jsonContent<Body>()?.message).isEqualTo("BYE")
  }

  @Test
  fun returnsContentRepeatedly() {
    val contents = "hello!".toByteArray(Charsets.UTF_8)
    val stream = ByteArrayInputStream(contents)
    val request = FakeHttpRequest("GET", "https://something.com") {
      throw IllegalArgumentException("should not execute")
    }
    request.setStreamingContent {
      // This drains the stream, thus making it unusable after a single call
      var b = stream.read()
      while (b != -1) {
        it.write(b)
        b = stream.read()
      }
    }

    assertThat(request.content).isEqualTo(contents)
    assertThat(request.content).isEqualTo(contents)
  }
}
