package misk.web.sse

import okhttp3.Headers
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ServerSentEventMarshallerTest {

  @Test
  fun `marshal and unmarshal simple event with data only`() {
    val event = ServerSentEvent(data = "Hello, World!")

    val marshalled = marshalEvent(event)
    assertThat(marshalled).isEqualTo("data: Hello, World!\r\n\r\n")

    val unmarshalled = unmarshalEvent(marshalled)
    assertThat(unmarshalled).isEqualTo(event)
  }

  @Test
  fun `marshal and unmarshal event with all fields`() {
    val event = ServerSentEvent(
      data = "Test data",
      event = "message",
      id = "123",
      retry = 5000L,
      comments = "This is a comment",
    )

    val marshalled = marshalEvent(event)
    val expectedOutput = """
      |event: message
      |data: Test data
      |id: 123
      |retry: 5000
      |: This is a comment
      |
      |""".trimMargin().replace("\n", "\r\n")

    assertThat(marshalled).isEqualTo(expectedOutput)

    val unmarshalled = unmarshalEvent(marshalled)
    assertThat(unmarshalled).isEqualTo(event)
  }

  @Test
  fun `marshal and unmarshal event with multiline data`() {
    val event = ServerSentEvent(
      data = "Line 1\nLine 2\nLine 3",
      event = "multiline",
    )

    val marshalled = marshalEvent(event)
    val expectedOutput = """
      |event: multiline
      |data: Line 1
      |data: Line 2
      |data: Line 3
      |
      |""".trimMargin().replace("\n", "\r\n")

    assertThat(marshalled).isEqualTo(expectedOutput)

    val unmarshalled = unmarshalEvent(marshalled)
    assertThat(unmarshalled).isEqualTo(event)
  }

  @Test
  fun `marshal and unmarshal event with multiline comments`() {
    val event = ServerSentEvent(
      data = "Some data",
      comments = "Comment line 1\nComment line 2",
    )

    val marshalled = marshalEvent(event)
    val expectedOutput = """
      |data: Some data
      |: Comment line 1
      |: Comment line 2
      |
      |""".trimMargin().replace("\n", "\r\n")

    assertThat(marshalled).isEqualTo(expectedOutput)

    val unmarshalled = unmarshalEvent(marshalled)
    assertThat(unmarshalled).isEqualTo(event)
  }

  @Test
  fun `marshal and unmarshal empty event`() {
    val event = ServerSentEvent()

    val marshalled = marshalEvent(event)
    assertThat(marshalled).isEqualTo("\r\n")

    val unmarshalled = unmarshalEvent(marshalled)
    assertThat(unmarshalled).isEqualTo(event)
  }

  @Test
  fun `marshal and unmarshal event with different line endings`() {
    // Test that data with different line endings is properly normalized
    val event = ServerSentEvent(
      data = "Line 1\r\nLine 2\rLine 3\nLine 4",
    )

    val marshalled = marshalEvent(event)
    val expectedOutput = """
      |data: Line 1
      |data: Line 2
      |data: Line 3
      |data: Line 4
      |
      |""".trimMargin().replace("\n", "\r\n")

    assertThat(marshalled).isEqualTo(expectedOutput)

    val unmarshalled = unmarshalEvent(marshalled)
    // The unmarshalled data should have normalized line endings
    assertThat(unmarshalled.data).isEqualTo("Line 1\nLine 2\nLine 3\nLine 4")
  }

  @Test
  fun `unmarshal event with extra whitespace`() {
    // SSE spec allows for optional space after colon
    val input = "event:message\r\ndata: Test data  \r\nid:123\r\n\r\n"

    val unmarshalled = unmarshalEvent(input)
    assertThat(unmarshalled).isEqualTo(
      ServerSentEvent(
        data = "Test data  ", // Trailing spaces are preserved
        event = "message",
        id = "123",
      ),
    )
  }

  @Test
  fun `unmarshal ignores unknown fields`() {
    val input = "event: test\r\nunknown: field\r\ndata: Test\r\n\r\n"

    val unmarshalled = unmarshalEvent(input)
    assertThat(unmarshalled).isEqualTo(
      ServerSentEvent(
        data = "Test",
        event = "test",
      ),
    )
  }

  private fun marshalEvent(event: ServerSentEvent): String {
    val responseBody = ServerSentEventMarshaller.responseBody(event)
    val buffer = Buffer()
    responseBody.writeTo(buffer)
    return buffer.readUtf8()
  }

  private fun unmarshalEvent(input: String): ServerSentEvent {
    val buffer = Buffer()
    buffer.writeUtf8(input)

    // Use the actual ServerSentEventUnmarshaller
    return ServerSentEventUnmarshaller.unmarshal(Headers.headersOf(), buffer) as ServerSentEvent
  }
}
