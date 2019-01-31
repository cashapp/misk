package misk.hibernate

interface CowritesDetector {

  fun pushSuppressChecks()
  fun popSuppressChecks()

  companion object {
    val NONE: CowritesDetector = object : CowritesDetector {
      override fun pushSuppressChecks() {}

      override fun popSuppressChecks() {}
    }
  }
}
