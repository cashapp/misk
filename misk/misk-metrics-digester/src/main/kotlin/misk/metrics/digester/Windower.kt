package misk.metrics.digester

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Window represents a time range */
data class Window(val start: ZonedDateTime, val end: ZonedDateTime) {
  /** Returns true if the given time t falls within the window's Start <= t < End. */
  fun contains(t: ZonedDateTime): Boolean {
    return !t.isBefore(start) && t.isBefore(end)
  }
}

/** Windower contains multiple windows with a specified duration */
class Windower(windowSecs: Int, stagger: Int) {
  private val windowSize: Long
  internal val startSecs: List<Int>

  /**
   * Creates a new Windower, which will create windows of windowSecs duration.
   * The size of the window must be in the range of 1 to 60 seconds, and must divide 60 without any remainder:
   * 1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30 second window sizes are permitted.
   * Stagger defines how many windows will contain a single time; a value of 1 means windows never overlap.
   */
  init {
    require(windowSecs > 0 || windowSecs <= 60) {
      "windowSecs must be in the range of (0, 60]"
    }
    require(60 % windowSecs == 0) {
      "60 % windowSecs must be 0, not ${60 % windowSecs}"
    }
    require(stagger in 1..windowSecs) {
      "stagger must be >= 1 and <= windowSecs"
    }
    var start = 0
    val startSecsMutableList = mutableListOf<Int>()
    while (start < 60) {
      startSecsMutableList.add(start)
      for (i in 1 until stagger) {
        startSecsMutableList.add(start + i * windowSecs / stagger)
      }
      start += windowSecs
    }

    startSecs = startSecsMutableList.toList()
    windowSize = windowSecs.toLong()
  }

  /**
   * Returns all windows that the given time falls into.
   * The returned slice will be ordered by window start time, and
   * the number of windows in the returned slice will the same as the stagger
   * given when the Windower was created.
   */
  fun windowsContaining(t: ZonedDateTime): List<Window> {
    // Find the earliest possible time the window could start,
    // then round up to the nearest second boundary
    var windowStart = t.minusSeconds(windowSize)
    var boundaryIndex = 0
    for (i in 0 until startSecs.count()) {
      if (startSecs[i] >= windowStart.second) {
        boundaryIndex = i
        break
      }
    }
    windowStart = windowStart.truncatedTo(ChronoUnit.SECONDS).withSecond(startSecs[boundaryIndex])
    var windowEnd = windowStart.plusSeconds(windowSize)
    // Keep creating potential windows until none hold the given time
    val windows: MutableList<Window> = mutableListOf()
    var currWindow = Window(windowStart, windowEnd)
    while (true) {
      if (currWindow.contains(t)) {
        windows.add(currWindow)
      } else if (windows.count() > 0) {
        // if len(windows) == 0 that means the first potential window started too early.
        // otherwise, len(windows) > 0 means that all possible windows were already appended
        break
      }
      val (start, index) = nextTime(windowStart, boundaryIndex)
      windowStart = start
      boundaryIndex = index
      windowEnd = windowStart.plusSeconds(windowSize)
      currWindow = Window(windowStart, windowEnd)
    }
    return windows
  }

  /** Returns index of next window in the minute if one exists or 0 if none left */
  private fun nextBoundaryIndex(i: Int): Int {
    if (i + 1 >= startSecs.count()) {
      return 0
    }
    return i + 1
  }

  /**
   * Returns the start time of the next window and boundary index of where
   * the window is within the current minute
   */
  private fun nextTime(start: ZonedDateTime, startBoundary: Int): Pair<ZonedDateTime, Int> {
    val boundaryIndex = nextBoundaryIndex(startBoundary)
    if (boundaryIndex == 0) {
      return Pair(start.withSecond(0).plusMinutes(1), boundaryIndex)
    }
    return Pair(start.withSecond(startSecs[boundaryIndex]), boundaryIndex)
  }
}
