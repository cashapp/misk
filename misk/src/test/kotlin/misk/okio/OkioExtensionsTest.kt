package misk.okio

import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OkioExtensionsTest {
  @Test
  fun split() {
    val input = """Page 1
                  |Page 1
                  |Page 1
                  |
                  |Page 2
                  |Page 2
                  |Page 2
                  |Page 2
                  |
                  |Page 3
                  |Page 3
                  |Page 3
                  |Page 3
                  |Page 3""".trimMargin()
    val split = Buffer().writeUtf8(input).split("\n\n".encodeUtf8()).toList()

    assertEquals("""
      Page 1
      Page 1
      Page 1
      """.trimIndent(), split[0].readUtf8())
    assertEquals("""
      Page 2
      Page 2
      Page 2
      Page 2
      """.trimIndent(), split[1].readUtf8())
    assertEquals("""
      Page 3
      Page 3
      Page 3
      Page 3
      Page 3
      """.trimIndent(), split[2].readUtf8())
  }
}
