package misk.metrics.digester

import java.time.ZoneId
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WindowerTest {
  @Test
  fun testWindowerInitialBoundaries() {
    assertThat(Windower(60, 1).startSecs).isEqualTo(mutableListOf(0))
    assertThat(Windower(30, 1).startSecs).isEqualTo(mutableListOf(0, 30))
    assertThat(Windower(60, 2).startSecs).isEqualTo(mutableListOf(0, 30))
    assertThat(Windower(60, 3).startSecs).isEqualTo(mutableListOf(0, 20, 40))
  }

  private data class WindowerTestClass(
    val description: String,
    val windowSecs: Int,
    val count: Int,
    val at: ZonedDateTime,
    val expected: List<Window>,
  )

  @Test
  fun testWindower() {
    val testCases: Array<WindowerTestClass> =
      arrayOf(
        WindowerTestClass(
          "1 min size at 1/1 window start",
          60,
          1,
          ZonedDateTime.of(2018, 5, 10, 15, 20, 0, 0, ZoneId.of("UTC")),
          listOf(
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 0, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 0, 0, ZoneId.of("UTC")),
            )
          ),
        ),
        WindowerTestClass(
          "1 min size at 1/1 window window",
          60,
          1,
          ZonedDateTime.of(2018, 5, 10, 15, 20, 1, 0, ZoneId.of("UTC")),
          listOf(
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 0, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 0, 0, ZoneId.of("UTC")),
            )
          ),
        ),
        WindowerTestClass(
          "1 min size at 1/2 window start",
          60,
          2,
          ZonedDateTime.of(2018, 5, 10, 15, 20, 0, 0, ZoneId.of("UTC")),
          listOf(
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 19, 30, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 20, 30, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 0, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 0, 0, ZoneId.of("UTC")),
            ),
          ),
        ),
        WindowerTestClass(
          "1 min size inside 1/2 window",
          60,
          2,
          ZonedDateTime.of(2018, 5, 10, 15, 20, 15, 0, ZoneId.of("UTC")),
          listOf(
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 19, 30, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 20, 30, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 0, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 0, 0, ZoneId.of("UTC")),
            ),
          ),
        ),
        WindowerTestClass(
          "1 min size at 2/2 window start",
          60,
          2,
          ZonedDateTime.of(2018, 5, 10, 15, 20, 30, 0, ZoneId.of("UTC")),
          listOf(
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 0, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 0, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 30, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 30, 0, ZoneId.of("UTC")),
            ),
          ),
        ),
        WindowerTestClass(
          "1 min size inside 2/2 window",
          60,
          2,
          ZonedDateTime.of(2018, 5, 10, 15, 20, 45, 0, ZoneId.of("UTC")),
          listOf(
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 0, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 0, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 30, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 30, 0, ZoneId.of("UTC")),
            ),
          ),
        ),
        WindowerTestClass(
          "15 sec size at 1/3 window start",
          15,
          3,
          ZonedDateTime.of(2018, 5, 10, 15, 20, 45, 0, ZoneId.of("UTC")),
          listOf(
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 35, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 20, 50, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 40, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 20, 55, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 45, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 0, 0, ZoneId.of("UTC")),
            ),
          ),
        ),
        WindowerTestClass(
          "15 sec size at 3/3 window start",
          15,
          3,
          ZonedDateTime.of(2018, 5, 10, 15, 20, 55, 0, ZoneId.of("UTC")),
          listOf(
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 45, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 0, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 50, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 5, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 55, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 10, 0, ZoneId.of("UTC")),
            ),
          ),
        ),
        WindowerTestClass(
          "15 sec size inside 3/3 window",
          15,
          3,
          ZonedDateTime.of(2018, 5, 10, 15, 20, 59, 0, ZoneId.of("UTC")),
          listOf(
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 45, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 0, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 50, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 5, 0, ZoneId.of("UTC")),
            ),
            Window(
              ZonedDateTime.of(2018, 5, 10, 15, 20, 55, 0, ZoneId.of("UTC")),
              ZonedDateTime.of(2018, 5, 10, 15, 21, 10, 0, ZoneId.of("UTC")),
            ),
          ),
        ),
      )

    testCases.forEach { t ->
      val windowContaining = Windower(t.windowSecs, t.count).windowsContaining(t.at)
      assertThat(windowContaining).isEqualTo(t.expected)
    }
  }
}
