package misk.digester

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Calendar
import kotlin.test.assertEquals

class WindowerTest {
  @Test
  fun testWindowerInitialBoundaries() {
    var w = Windower()
    w.NewWindower(60, 1)
    assertThat(w.startSecs).isEqualTo(mutableListOf(0))

    w.NewWindower(30, 1)
    assertThat(w.startSecs).isEqualTo(mutableListOf(0, 30))

    w.NewWindower(60, 2)
    assertThat(w.startSecs).isEqualTo(mutableListOf(0, 30))

    w.NewWindower(60, 3)
    assertThat(w.startSecs).isEqualTo(mutableListOf(0, 20, 40))
  }

  private data class WindowerTestClass(
    val description: String,
    val windowSecs: Int,
    val count: Int,
    //the time of which to look for the windows
    val at: WindowerTestClassCalendar,
    //windows expected to exist at that time
    val expected: Array<WindowTestClass>
  )

  // represents a single window set by the start and the end date
  private class WindowTestClass(
    val start: WindowerTestClassCalendar,
    val end: WindowerTestClassCalendar
  )

  private data class WindowerTestClassCalendar(
    val year: Int,
    val month: Int,
    val date: Int,
    val hrs: Int,
    val min: Int,
    val sec: Int
  )

  @Test
  fun testWindower() {
    val testCases: Array<WindowerTestClass>
        = arrayOf(
        WindowerTestClass(
            "1 min size at 1/1 window start",
            60,
            1,
             WindowerTestClassCalendar(2018, 5, 10, 15, 20, 0),
             arrayOf(
                WindowTestClass(
                     WindowerTestClassCalendar(2018, 5, 10, 15, 20, 0),
                     WindowerTestClassCalendar(2018, 5, 10, 15, 21, 0)
                 )
             )
        ),
        WindowerTestClass(
            "1 min size at 1/1 window window",
            60,
            1,
            WindowerTestClassCalendar(2018, 5, 10, 15, 20, 1),
            arrayOf(
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 0),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 0)
                )
            )
        )

    )

    testCases.forEachIndexed { i, t ->
      val windower = Windower()
      windower.NewWindower(t.windowSecs, t.count)

      // populate expected list by generating the window
      val exCalendar: MutableList<Window> = mutableListOf()
      t.expected.forEach{ c ->
        val startCalender = Calendar.getInstance()
        val endCalendar = Calendar.getInstance()
        startCalender.set(c.start.year, c.start.month, c.start.date, c.start.hrs, c.start.min, c.start.sec)
        startCalender.set(Calendar.MILLISECOND, 0)
        endCalendar.set(c.end.year, c.end.month, c.end.date, c.end.hrs, c.end.min, c.end.sec)
        endCalendar.set(Calendar.MILLISECOND, 0)
        val window = Window(startCalender, endCalendar)
        exCalendar.add(window)
      }

      val atCalendar: Calendar = Calendar.getInstance()
      atCalendar.set(t.at.year, t.at.month, t.at.date, t.at.hrs, t.at.min, t.at.sec)
      atCalendar.set(Calendar.MILLISECOND, 0)

      // get the windows returned by the windower
      val windowContaining = windower.windowContaining(atCalendar)

      // check size of expected list is equal to output of windower
      assertEquals(exCalendar.count(), windowContaining.count(), "${t.description}. Test case: $i")

      // check all values and order of windower is equal to expected
      windowContaining.forEachIndexed { ii, window ->
        assertEquals(exCalendar[ii].string(), window.string(), "${t.description}. Test case: $i")
      }
    }
  }
}