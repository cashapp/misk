package misk.web.dashboard

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ValidWebEntryTest {
  @Test
  fun `happy path`() {
    class TestWebEntry : ValidWebEntry(valid_slug = "test-slug", valid_url_path_prefix = "/test-path/")
    TestWebEntry()
  }

  @Test
  fun `empty happy path`() {
    class TestWebEntry : ValidWebEntry()
    TestWebEntry()
  }

  @Test
  fun `fails on bad url path prefix missing trailing slash`() {
    val classException =
      assertFailsWith<IllegalArgumentException> {
        class TestWebEntry : ValidWebEntry(valid_slug = "test-slug", valid_url_path_prefix = "/test-path")
        TestWebEntry()
      }
    assertEquals(
      "Invalid or unexpected [urlPathPrefix=/test-path]. " + "Must start with 'http' OR start and end with '/'.",
      classException.message,
    )
  }

  @Test
  fun `fails on bad url path prefix missing starting slash`() {
    val classException =
      assertFailsWith<IllegalArgumentException> {
        class TestWebEntry : ValidWebEntry(valid_slug = "test-slug", valid_url_path_prefix = "test-path/")
        TestWebEntry()
      }
    assertEquals(
      "Invalid or unexpected [urlPathPrefix=test-path/]. " + "Must start with 'http' OR start and end with '/'.",
      classException.message,
    )
  }

  @Test
  fun `fails on bad url path prefix not starting with http if absolute path`() {
    val classException =
      assertFailsWith<IllegalArgumentException> {
        class TestWebEntry : ValidWebEntry(valid_slug = "test-slug", valid_url_path_prefix = "www.cash.app/test-path")
        TestWebEntry()
      }
    assertEquals(
      "Invalid or unexpected [urlPathPrefix=www.cash.app/test-path]. " +
        "Must start with 'http' OR start and end with '/'.",
      classException.message,
    )
  }

  @Test
  fun `fails on blocked prefix`() {
    val classException =
      assertFailsWith<IllegalArgumentException> {
        class TestWebEntry : ValidWebEntry(valid_slug = "test-slug", valid_url_path_prefix = "/api/test-path/")
        TestWebEntry()
      }
    assertEquals("[urlPathPrefix=/api/test-path/] begins with a blocked prefix: [/api/].", classException.message)
  }

  @Test
  fun `fails on invalid slug characters`() {
    val classException =
      assertFailsWith<IllegalArgumentException> {
        class TestWebEntry : ValidWebEntry(valid_slug = "!test-slug", valid_url_path_prefix = "/test-path/")
        TestWebEntry()
      }
    assertEquals(
      "[slug=!test-slug] contains invalid characters. " + "Can only contain characters in ranges [a-z], [0-9] or '-'.",
      classException.message,
    )
  }

  @Test
  fun `allows valid path with query parameters`() {
    class TestWebEntry : ValidWebEntry(valid_slug = "test-slug", valid_url_path_prefix = "/test-slug/?q=abc123")
    TestWebEntry()
  }
}
