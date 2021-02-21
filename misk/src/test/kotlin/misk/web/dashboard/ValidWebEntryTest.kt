package misk.web.dashboard

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidWebEntryTest {
  @Test fun `happy path`() {
    class TestWebEntry : ValidWebEntry(slug = "test-slug", url_path_prefix = "/test-path/")
    TestWebEntry()
  }

  @Test fun `empty happy path`() {
    class TestWebEntry : ValidWebEntry()
    TestWebEntry()
  }

  @Test fun `fails on bad url path prefix missing trailing slash`() {
    val errMsg = assertFailsWith<IllegalArgumentException> {
      class TestWebEntry : ValidWebEntry(slug = "test-slug", url_path_prefix = "/test-path")
      TestWebEntry()
    }
    assertEquals(
      "Invalid or unexpected url path prefix: '/test-path'. " +
        "Must start with 'http' OR start and end with '/'.",
      errMsg.message
    )
  }

  @Test fun `fails on bad url path prefix missing starting slash`() {
    val errMsg = assertFailsWith<IllegalArgumentException> {
      class TestWebEntry : ValidWebEntry(slug = "test-slug", url_path_prefix = "test-path/")
      TestWebEntry()
    }
    assertEquals(
      "Invalid or unexpected url path prefix: 'test-path/'. " +
        "Must start with 'http' OR start and end with '/'.",
      errMsg.message
    )
  }

  @Test fun `fails on bad url path prefix not starting with http if absolute path`() {
    val errMsg = assertFailsWith<IllegalArgumentException> {
      class TestWebEntry : ValidWebEntry(
        slug = "test-slug",
        url_path_prefix = "www.cash.app/test-path"
      )
      TestWebEntry()
    }
    assertEquals(
      "Invalid or unexpected url path prefix: 'www.cash.app/test-path'. " +
        "Must start with 'http' OR start and end with '/'.",
      errMsg.message
    )
  }

  @Test fun `fails on blocked prefix`() {
    val errMsg = assertFailsWith<IllegalArgumentException> {
      class TestWebEntry : ValidWebEntry(slug = "test-slug", url_path_prefix = "/api/test-path/")
      TestWebEntry()
    }
    assertEquals("Url path prefix begins with a blocked prefix: [/api/].", errMsg.message)
  }

  @Test fun `fails on invalid slug characters`() {
    val errMsg = assertFailsWith<IllegalArgumentException> {
      class TestWebEntry : ValidWebEntry(slug = "!test-slug", url_path_prefix = "/test-path/")
      TestWebEntry()
    }
    assertEquals(
      "Slug contains invalid characters. " +
        "Can only contain characters in ranges [a-z], [0-9] or '-'.",
      errMsg.message
    )
  }
}
