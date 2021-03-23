package misk.logging

/**
 * Collects log messages so they may be asserted on for testing.
 *
 * To use it, add the [LogCollectorModule] to your test. You’ll need `@MiskTest` with services
 * started because this uses a service to install a log appender for the duration of the test.
 *
 * In your test method you should perform some action that logs, then use an injected `logCollector`
 * to verify that the expected logs were emitted:
 *
 * ```
 * @MiskTest(startService = true)
 * class MyTest {
 *   @MiskTestModule
 *   val module = Modules.combine(
 *       MiskServiceModule(),
 *       LogCollectorModule(),
 *       ...
 *   )
 *
 *   @Inject lateinit var logCollector: LogCollector
 *
 *   @Test
 *   fun test() {
 *     someMethodThatLogs()
 *     assertThat(logCollector.takeMessages()).containsExactly("this is the only logged message!")
 *   }
 * }
 * ```
 *
 * Use the optional parameters of [takeMessages] to constrain which log messages are returned.
 */
@Deprecated(
  message = "Use wisp-logging -> wisp.logging.LogCollector",
  replaceWith = ReplaceWith("LogCollector", "wisp.logging.LogCollector")
)
interface LogCollector : wisp.logging.LogCollector
