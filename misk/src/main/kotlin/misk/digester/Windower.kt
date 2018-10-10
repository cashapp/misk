package misk.digester

import java.util.Date

/** Window reperesnts a time range *//
class Window {

  private val start: Date
  private val end: Date

  constructor(start: Date, end: Date) {
    this.start = start
    this.end = end
  }

  /** Returns true if the given time falls within the window's start and end time */
  fun contains(t: Date): Boolean {
    return t.after(start) && t.before(end)
  }

  /** Returns true when the other window has the same start and end times. */
  fun equals(other: Window): Boolean {
    return start.time == other.start.time && end.time == other.end.time
  }

  /** Returns string representing the start and end time */
  fun string(): String {
    return "[${start.time}, ${end.time})"
  }
}

class Windower {
  private val windowSize: Long
  private val startSecs: MutableList<Int>

  /**
   * Creates a new Windower, which will create windows of windowSecs duration.
   * The size of the window must be in the range of 1 to 60 seconds, and must divide 60 without any remainder:
   * 1, 2, 3, 4, 5, 6, 10, 12, 15, 20, 30 second window sizes are permitted.
   * Stagger defines how many windows will contain a single time; a value of 1 means windows never overlap.
   */
  fun NewWindower(windowSecs: Int, stagger: Int) {
    require (windowSecs > 0 || windowSecs <= 60)  {
      "windowSecs must be in the range of (0, 60]"
    }
    require (60 % windowSecs != 0) {
      "60 % windowSecs must be 0, not ${60 % windowSecs}"
    }
    require(stagger > 0 && stagger <= windowSecs) {
      "stagger must be >= 1 and <= windowSecs"
    }

    var startSecs

  }
}