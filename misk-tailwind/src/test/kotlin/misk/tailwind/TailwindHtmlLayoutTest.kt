package misk.tailwind

import kotlinx.html.stream.appendHTML
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TailwindHtmlLayoutTest {

  private fun renderLayout(enableTurbo: Boolean): String =
    StringBuilder().apply {
      appendHTML().TailwindHtmlLayout(
        appRoot = "/app",
        title = "Test",
        enableTurbo = enableTurbo,
      ) {}
    }.toString()

  @Test
  fun `turbo scripts included by default`() {
    val html = renderLayout(enableTurbo = true)
    assertContains(html, "turbo/7.2.5/es2017-umd.min.js")
    assertContains(html, "turbo-root")
  }

  @Test
  fun `turbo scripts excluded when enableTurbo is false`() {
    val html = renderLayout(enableTurbo = false)
    assertFalse(html.contains("turbo/7.2.5/es2017-umd.min.js"))
    assertFalse(html.contains("turbo-root"))
  }
}
