package misk.web

import misk.web.mediatype.asMediaRange
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.assertj.core.api.assertOrdering
import org.junit.jupiter.api.Test

internal class RequestMatchTest {
  @Test
  fun preferMostSpecificPath() {
    assertOrdering(
      match(PathPattern.parse("/org/admin/users"), "text/*", "text/*"),
      match(PathPattern.parse("/org/{folder}/{type}"), "text/plain", "text/plain"),
      match(PathPattern.parse("{path:.*}"), "text/plain", "text/plain")
    )
  }

  @Test
  fun preferMostSpecificContentTypes() {
    assertOrdering(
      match("text/plain", "text/plain"),
      match("text/plain", "text/*"),
      match("text/plain", "*/*"),
      match("text/*", "text/plain"),
      match("text/*", "text/*"),
      match("text/*", "*/*"),
      match("*/*", "text/plain"),
      match("*/*", "text/*"),
      match("*/*", "*/*")
    )
  }

  private fun match(
    requestRange: String,
    responseType: String
  ) =
    match(PathPattern.parse("/a"), requestRange, responseType)

  private fun match(
    path: PathPattern,
    requestRange: String,
    responseType: String
  ) =
    RequestMatch(
      path,
      requestRange.asMediaRange(),
      false,
      responseType.toMediaTypeOrNull()!!
    )
}
