package misk.spirit

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

@Timeout(10)
class SpiritTest {
  @TempDir lateinit var tempDir: File

  private val ddl = "CREATE TABLE widgets (id BIGINT NOT NULL PRIMARY KEY);"

  @Test
  fun stderrWarningsAreNotDdl() {
    val result =
      diff(
        """
      echo '2026-10-26 WARN server does not support TLS' >&2
      echo '-- Lint information'
      echo '$ddl'
      """
          .trimIndent()
      )

    assertThat(result).isEqualTo(SchemaDiff(ddl, true))
  }

  @Test
  fun noDiffWithStderrWarnings() {
    val result =
      diff(
        """
        echo 'WARN server does not support TLS' >&2
        echo '-- No schema differences found.'
        """
          .trimIndent()
      )

    assertThat(result).isEqualTo(SchemaDiff(null, false))
  }

  @Test
  fun stderrCannotSuppressDdl() {
    val result =
      diff(
        """
      echo '-- No schema differences found.' >&2
      echo '$ddl'
      """
          .trimIndent()
      )

    assertThat(result).isEqualTo(SchemaDiff(ddl, true))
  }

  @Test
  fun schemaLoadFailureIncludesStderr() {
    assertThatIllegalStateException()
      .isThrownBy {
        diff(
          """
          echo 'Loading schema'
          echo 'Unable to load schema' >&2
          exit 2
          """
            .trimIndent()
        )
      }
      .withMessageContaining("Loading schema")
      .withMessageContaining("Unable to load schema")
  }

  @Test
  fun largeStderrDoesNotBlockLintDiff() {
    val result =
      diff(
        """
      awk 'BEGIN { for (i = 0; i < 16384; i++) print "diagnostic warning" }' >&2
      echo '-- Lint violation'
      echo '$ddl'
      exit 1
      """
          .trimIndent()
      )

    assertThat(result.hasDiff).isTrue()
    assertThat(result.diff?.length).isEqualTo(ddl.length)
    assertThat(result.diff).isEqualTo(ddl)
  }

  private fun diff(script: String): SchemaDiff {
    val executable =
      File(tempDir, "spirit").apply {
        writeText("#!/bin/sh\nset -eu\n$script\n")
        check(setExecutable(true))
      }
    return Spirit { executable.absolutePath }.diff("unused", mapOf("widgets.sql" to ddl))
  }
}
