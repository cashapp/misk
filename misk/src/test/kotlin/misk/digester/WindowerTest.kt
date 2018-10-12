package misk.digester

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Calendar
import kotlin.test.assertEquals

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
    val at: WindowerTestClassCalendar,
    val expected: Array<WindowTestClass>
  )

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
        ),
        WindowerTestClass(
            "1 min size at 1/2 window start",
            60,
            2,
            WindowerTestClassCalendar(2018, 5, 10, 15, 20, 0),
            arrayOf(
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 19, 30),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 30)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 0),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 0)
                )
            )
        ),
        WindowerTestClass(
            "1 min size inside 1/2 window",
            60,
            2,
            WindowerTestClassCalendar(2018, 5, 10, 15, 20, 15),
            arrayOf(
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 19, 30),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 30)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 0),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 0)
                )
            )
        ),
        WindowerTestClass(
            "1 min size at 2/2 window start",
            60,
            2,
            WindowerTestClassCalendar(2018, 5, 10, 15, 20, 30),
            arrayOf(
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 0),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 0)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 30),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 30)
                )
            )
        ),
        WindowerTestClass(
            "1 min size inside 2/2 window",
            60,
            2,
            WindowerTestClassCalendar(2018, 5, 10, 15, 20, 45),
            arrayOf(
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 0),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 0)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 30),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 30)
                )
            )
        ),
        WindowerTestClass(
            "15 sec size at 1/3 window start",
            15,
            3,
            WindowerTestClassCalendar(2018, 5, 10, 15, 20, 45),
            arrayOf(
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 35),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 50)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 40),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 55)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 45),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 0)
                )
            )
        ),
        WindowerTestClass(
            "15 sec size at 3/3 window start",
            15,
            3,
            WindowerTestClassCalendar(2018, 5, 10, 15, 20, 55),
            arrayOf(
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 45),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 0)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 50),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 5)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 55),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 10)
                )
            )
        ),
        WindowerTestClass(
            "15 sec size inside 3/3 window",
            15,
            3,
            WindowerTestClassCalendar(2018, 5, 10, 15, 20, 59),
            arrayOf(
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 45),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 0)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 50),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 5)
                ),
                WindowTestClass(
                    WindowerTestClassCalendar(2018, 5, 10, 15, 20, 55),
                    WindowerTestClassCalendar(2018, 5, 10, 15, 21, 10)
                )
            )
        )
    )

    testCases.forEachIndexed { i, t ->
      // Create the calendar instance of the time to check at
      val atCalendar: Calendar = Calendar.getInstance()
      atCalendar.set(t.at.year, t.at.month, t.at.date, t.at.hrs, t.at.min, t.at.sec)
      atCalendar.set(Calendar.MILLISECOND, 0)

      // Generate list of expected windows
      val exCalendar: MutableList<Window> = mutableListOf()
      t.expected.forEach{ c ->
        val startCalender = Calendar.getInstance()
        startCalender.set(c.start.year, c.start.month, c.start.date, c.start.hrs, c.start.min, c.start.sec)
        startCalender.set(Calendar.MILLISECOND, 0)

        val endCalendar = Calendar.getInstance()
        endCalendar.set(c.end.year, c.end.month, c.end.date, c.end.hrs, c.end.min, c.end.sec)
        endCalendar.set(Calendar.MILLISECOND, 0)
        
        val window = Window(startCalender, endCalendar)
        exCalendar.add(window)
      }

      // Get list of returned windows
      val windower = Windower(t.windowSecs, t.count)
      val windowContaining = windower.windowContaining(atCalendar)

      // Check number of windows to equal to expected
      assertEquals(exCalendar.count(), windowContaining.count(), "${t.description}. Window amount test case: $i")

      // Check all values and order of windows is equal to expected
      windowContaining.forEachIndexed { ii, window ->
        assertEquals(exCalendar[ii].string(), window.string(), "${t.description}. Window value test case: $i, Window: $ii")
      }
    }
  }
}