package misk.web.ssl

internal object Http2Testing {
  /**
   * From Java 9 forward Runtime.Version tells us what we need to know. On earlier releases we just
   * probe the presence or absence of that class.
   *
   * OkHttp's HTTP/2 just works on Java 9. On earlier releases it needs extra configuration.
   */
  fun isJava9OrNewer(): Boolean {
    try {
      Class.forName("java.lang.Runtime\$Version")
      return true
    } catch (e: ClassNotFoundException) {
      return false
    }
  }
}