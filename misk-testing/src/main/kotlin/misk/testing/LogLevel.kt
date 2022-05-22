package misk.testing

annotation class LogLevel(val level: Level = Level.INFO) {
  enum class Level {
    DEBUG,
    INFO,
    WARN,
    ERROR,
  }
}

