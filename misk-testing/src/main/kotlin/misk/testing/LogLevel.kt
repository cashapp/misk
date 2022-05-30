package misk.testing

/**
 *
 * Annotate your test classes or methods with `@LogLevel` to change the log level used by the test.
 * It can be useful to have different LogLevel by test, specially if you're debugging issues on
 * CI.
 *
 * You can annotate methods:
 *
 * ```
 * @LogLevel(level = LogLevel.Level.DEBUG)
 * @Test fun levelDebug() {
 * }
 * ```
 *
 * Also, You can annotate test classes:
 * ```
 * @LogLevel(level = LogLevel.Level.ERROR)
 * class `you can annotate the test` {
 * @Test fun levelError() {
 * ```
 *
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class LogLevel(val level: Level = Level.INFO) {
  enum class Level {
    DEBUG,
    INFO,
    WARN,
    ERROR,
  }
}

