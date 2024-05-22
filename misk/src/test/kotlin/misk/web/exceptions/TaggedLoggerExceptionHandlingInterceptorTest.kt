package misk.web.exceptions

import ch.qos.logback.classic.Level
import com.google.common.testing.FakeTicker
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.web.exceptions.TaggedLoggerExceptionHandlingInterceptorTest.LogMDCContextTestAction.LogMDCContextTestActionLogger.Companion.getTaggedLogger
import misk.web.exceptions.TaggedLoggerExceptionHandlingInterceptorTest.NestedLoggersOuterExceptionHandled.ServiceExtendedTaggedLogger.Companion.getTaggedLoggerNestedOuterExceptionThrown
import misk.web.exceptions.TaggedLoggerExceptionHandlingInterceptorTest.NestedLoggersOuterExceptionHandledNoneThrown.ServiceExtendedTaggedLogger.Companion.getTaggedLoggerNestedOuterExceptionThrownThenNone
import misk.web.exceptions.TaggedLoggerExceptionHandlingInterceptorTest.NestedTaggedLoggersThrowsException.ServiceExtendedTaggedLogger.Companion.getTaggedLoggerNested
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.RequestHeaders
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.exceptions.TaggedLoggerExceptionHandlingInterceptorTest.NestedTaggedLoggersBothSucceed.ServiceExtendedTaggedLogger.Companion.getTaggedLoggerNestedThreads
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import mu.KLogger
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import wisp.logging.Copyable
import wisp.logging.LogCollector
import wisp.logging.Tag
import wisp.logging.TaggedLogger
import wisp.logging.getLogger
import wisp.logging.info
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

@MiskTest(startService = true)
internal class TaggedLoggerExceptionHandlingInterceptorTest {
  @MiskTestModule
  val module = object :KAbstractModule() {
    override fun configure() {
      install(LogCollectorModule())
      install(AccessControlModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()

      install(WebActionModule.create<ServiceNotUsingTaggedLoggerShouldLogWithHandRolledMdcContext>())
      install(WebActionModule.create<ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext>())
      install(WebActionModule.create<LogMDCContextTestAction>())
      install(WebActionModule.create<NestedTaggedLoggersThrowsException>())
      install(WebActionModule.create<NestedLoggersOuterExceptionHandled>())
      install(WebActionModule.create<NestedLoggersOuterExceptionHandledNoneThrown>())
      install(WebActionModule.create<NestedTaggedLoggersBothSucceed>())
    }
  }
  val httpClient = OkHttpClient()

  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var fakeTicker: FakeTicker

  @BeforeEach
  fun setUp() {
    fakeTicker.setAutoIncrementStep(100L, TimeUnit.MILLISECONDS)
  }

  @Test
  fun serviceNotUsingTaggedLoggerShouldLogWithMdcContext() {
    val response = invoke(ServiceNotUsingTaggedLoggerShouldLogWithHandRolledMdcContext.URL, "caller")
    assertThat(response.code).isEqualTo(200)

    val logs =
      logCollector.takeEvents(ServiceNotUsingTaggedLoggerShouldLogWithHandRolledMdcContext::class)
    assertThat(logs).hasSize(1)
    assertThat(logs.single().message).isEqualTo("Start Test Process")
    assertThat(logs.single().level).isEqualTo(Level.INFO)
    assertThat(logs.single().mdcPropertyMap).containsEntry(
      "HandRolledLoggerTag",
      "handRolledLoggerTagValue"
    )
  }

  class ServiceNotUsingTaggedLoggerShouldLogWithHandRolledMdcContext @Inject constructor() :
    WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      logger.info(Tag("HandRolledLoggerTag", "handRolledLoggerTagValue")) { "Start Test Process" }
      return "value"
    }

    companion object {
      val logger = getLogger<ServiceNotUsingTaggedLoggerShouldLogWithHandRolledMdcContext>()
      const val URL = "/log/ServiceNotUsingTaggedLoggerShouldLogWithHandRolledMdcContext/test"
    }
  }


  // This test verifies the current mdc log tag approach still used in a lot of services works as expected
  @Test
  fun serviceNotUsingTaggedLoggerShouldLogExceptionWithoutMdcContext() {
    val response = invoke(ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(
      ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext::class, consumeUnmatchedLogs = false)
    val miskLogs = logCollector.takeEvents(ExceptionHandlingInterceptor::class)

    // The service info log had a legacy style TaggedLogger defined within the service using asContext
    // This will log regular messages with the specified mdc tags
    assertThat(serviceLogs).hasSize(1)
    with(serviceLogs.single()) {
      assertThat(message).isEqualTo("Test log with tags")
      assertThat(level).isEqualTo(Level.INFO)
      assertThat(mdcPropertyMap).containsEntry("tag123", "value123")
    }

    // But any exceptions thrown within and handled by misk interceptor do not have the mdc tags
    assertThat(miskLogs).hasSize(1)
    with(miskLogs.single()) {
      assertThat(throwableProxy.message).isEqualTo("Test Process Exception without tags")
      assertThat(message).contains("unexpected error dispatching to")
      assertThat(level).isEqualTo(Level.ERROR)
      assertThat(mdcPropertyMap).doesNotContainKey("tag123")
    }
  }

  class ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      LegacyTaggedLogger(getLogger<ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext>())
        .tag(Tag("tag123", "value123"))
        .asContext {
          getLogger<ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext>().info("Test log with tags")
          throw ServiceUsingLegacyTaggedLoggerException("Test Process Exception without tags")
        }
    }

    companion object {
      const val URL = "/log/ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext/test"
    }

    class ServiceUsingLegacyTaggedLoggerException(message: String): Throwable(message)

    // This is a class manually defined in many Cash services copied and pasted between them.
    // This simulates this legacy approach and verify still works as expected.
    data class LegacyTaggedLogger(
      val kLogger: KLogger,
      private val tags: Set<Tag> = emptySet()
    ) {
      fun tag(vararg newTags: Tag) = LegacyTaggedLogger(kLogger, tags.plus(newTags))

      fun <T> asContext(f: () -> T): T {
        val priorMDC = MDC.getCopyOfContextMap()
        try {
          tags.forEach { (k, v) ->
            if (v != null) {
              MDC.put(k, v.toString())
            }
          }
          return f()
        } finally {
          MDC.setContextMap(priorMDC ?: emptyMap())
        }
      }
    }
  }


  @Test
  fun shouldHaveLogAndExceptionFromServiceWithMdcContext() {
    val response = invoke(LogMDCContextTestAction.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(LogMDCContextTestAction::class, consumeUnmatchedLogs = false)
    val miskExceptionLogs = logCollector.takeEvents(ExceptionHandlingInterceptor::class)

    assertThat(serviceLogs).hasSize(1)
    assertThat(serviceLogs.first().mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")

    assertThat(miskExceptionLogs).hasSize(1)
    with(miskExceptionLogs.single()) {
      assertThat(throwableProxy.message).isEqualTo("Test Exception")
      assertThat(message).contains("unexpected error dispatching to")
      assertThat(level).isEqualTo(Level.ERROR)
      assertThat(mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
    }
  }

  class LogMDCContextTestAction @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      logger
        .testTag("SpecialTagValue123")
        .asContext {
          logger.info { "Tester" }
          throw LogMDCContextTestActionException("Test Exception")
        }
    }

    class LogMDCContextTestActionException(message: String) : Throwable(message)

    companion object {
      val logger = this::class.getTaggedLogger()
      const val URL = "/log/LogMDCContextTestAction/test"
    }

    data class LogMDCContextTestActionLogger<L: Any>(val logClass: KClass<L>, val tags: Set<Tag> = emptySet()): TaggedLogger<L, LogMDCContextTestActionLogger<L>>(logClass, tags),
    Copyable<LogMDCContextTestActionLogger<L>> {
      fun testTag(value: String) = tag(Tag("testTag", value))

      companion object {
        fun <T : Any> KClass<T>.getTaggedLogger(): LogMDCContextTestActionLogger<T> {
          return LogMDCContextTestActionLogger(this)
        }
      }

      override fun copyWithNewTags(newTags: Set<Tag>): LogMDCContextTestActionLogger<L> {
        return this.copy(tags = newTags)
      }
    }
  }

  @Test
  fun shouldHandleConcurrentThreadsUsingTaggedLogger() {
    // Start one instance of the service
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath(NestedTaggedLoggersBothSucceed.URL)
      .build()

    // Setup callable to call that service and get the response
    // Need to identify the caller by a unique ID to filter logs
    val callService: (String) -> Callable<String> = { identifier ->
      Callable {
        val request = okhttp3.Request.Builder()
          .url(url)
          .get()
          .addHeader(NestedTaggedLoggersBothSucceed.IDENTIFIER_HEADER, identifier)

        val response = OkHttpClient().newCall(request.build()).execute()

        val responseText = response.body!!.source().use { source ->
          source.readUtf8Line()
        }

        assertThat(response.code).isEqualTo(200)
        assertThat(responseText).isEqualTo("SUCCESS")
        return@Callable Thread.currentThread().name
      }
    }

    // Create concurrent calls to that service instance List(80) Threads(20)
    val executor = Executors.newFixedThreadPool(20)

    val results = List(80) { index ->
      println("Executing: $index")
      val out = executor.submit(callService("caller_$index"))
      out
    }.mapIndexed { index, future ->
      println("Getting: $index with value ${future.get()}")
    } // Executing thread name

    println("Done. Processed ${results.size} threads")

    val serviceLogs = logCollector
      .takeEvents(NestedTaggedLoggersBothSucceed::class, consumeUnmatchedLogs = false)
      .plus(logCollector.takeEvents(NestedTaggedLoggersBothSucceed.AnotherClass::class, consumeUnmatchedLogs = false))

    // Deterministically order all messages
    val serviceLogsOrdered = serviceLogs.filter { it.message == "Non nested log message" }
      .plus(serviceLogs.filter { it.message == "Nested log message with two mdc properties" })
      .plus(serviceLogs.filter { it.message == "Non nested log message after nest" })
      .plus(serviceLogs.filter { it.message == "Log message after TaggedLogger" })

    val miskExceptionLogs = logCollector.takeEvents(ExceptionHandlingInterceptor::class)

    serviceLogsOrdered.groupBy { it.mdcPropertyMap[NestedTaggedLoggersBothSucceed.EXECUTION_IDENTIFIER] }
      .forEach { (_, logs) ->
        assertThat(logs.first().message).isEqualTo("Non nested log message")
        assertThat(logs.first().mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
        assertThat(logs.first().mdcPropertyMap).doesNotContainKey("testTagNested")

        assertThat(logs[1].message).isEqualTo("Nested log message with two mdc properties")
        assertThat(logs[1].mdcPropertyMap).doesNotContainKey("testTag") // Context not shared into a newly spawned thread inside a tagged logger asContext
        assertThat(logs[1].mdcPropertyMap).containsEntry("testTagNested", "NestedTagValue123")

        assertThat(logs[2].message).isEqualTo("Non nested log message after nest")
        assertThat(logs[2].mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
        assertThat(logs[2].mdcPropertyMap).doesNotContainKey("testTagNested")

        assertThat(logs.last().message).isEqualTo("Log message after TaggedLogger")
        assertThat(logs.last().mdcPropertyMap).doesNotContainKey("testTag")
        assertThat(logs.last().mdcPropertyMap).doesNotContainKey("testTagNested")
      }

    assertThat(miskExceptionLogs).hasSize(0)
    println("Done")
  }

  class NestedTaggedLoggersBothSucceed @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@RequestHeaders headers: Headers): String {
      val result = logger
        .testTag("SpecialTagValue123")
        .tag("executionIdentifier" to headers[IDENTIFIER_HEADER])
        .asContext {
          logger.info { "Non nested log message" }
          var result = "NO RESULT"

          // Starting a new thread will not inherit the MDC context by default due to MDC being thread local
          Thread {
            result = AnotherClass().functionWithNestedTaggedLogger(headers[IDENTIFIER_HEADER])
          }.also { it.start() }.join(1000)

          logger.info { "Non nested log message after nest" }
          result
        }

      // Manually add this tag to identify the execution for verification
      logger.info(EXECUTION_IDENTIFIER to headers[IDENTIFIER_HEADER]) { "Log message after TaggedLogger" }
      return result
    }

    companion object {
      val logger = this::class.getTaggedLoggerNestedThreads()
      const val URL = "/log/NestedTaggedLoggersBothSucceed/test"
      const val IDENTIFIER_HEADER = "IDENTIFIER_HEADER"
      const val EXECUTION_IDENTIFIER = "executionIdentifier"
    }

    data class ServiceExtendedTaggedLogger<L: Any>(
      val logClass: KClass<L>,
      val tags: Set<Tag> = emptySet()
    ): TaggedLogger<L, ServiceExtendedTaggedLogger<L>>(logClass, tags), Copyable<ServiceExtendedTaggedLogger<L>> {

      fun testTag(value: String) = tag(Tag("testTag", value))
      fun testTagNested(value: String) = tag(Tag("testTagNested", value))

      companion object {
        fun <T : Any> KClass<T>.getTaggedLoggerNestedThreads(): ServiceExtendedTaggedLogger<T> {
          return ServiceExtendedTaggedLogger(this)
        }
      }

      override fun copyWithNewTags(newTags: Set<Tag>): ServiceExtendedTaggedLogger<L> {
        return this.copy(tags = newTags)
      }
    }

    class AnotherClass() {
      fun functionWithNestedTaggedLogger(parentTag: String?): String {
        return logger
          .tag("executionIdentifier" to parentTag)
          .testTagNested("NestedTagValue123")
          .asContext {
            logger.info { "Nested log message with two mdc properties" }
            "SUCCESS"
          }
      }

      companion object {
        val logger = this::class.getTaggedLoggerNestedThreads()
      }
    }
  }

  @Test
  fun shouldHaveLogAndExceptionsFromServiceWithNestedMdcContext() {
    val response = invoke(NestedTaggedLoggersThrowsException.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(NestedTaggedLoggersThrowsException::class, consumeUnmatchedLogs = false)
    val miskExceptionLogs = logCollector.takeEvents(ExceptionHandlingInterceptor::class)

    assertThat(serviceLogs).hasSize(2)
    assertThat(serviceLogs.first().message).isEqualTo("Non nested log message")
    assertThat(serviceLogs.first().mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
    assertThat(serviceLogs.first().mdcPropertyMap).doesNotContainKey("testTagNested")

    assertThat(serviceLogs.last().message).isEqualTo("Nested log message with two mdc properties")
    assertThat(serviceLogs.last().mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
    assertThat(serviceLogs.last().mdcPropertyMap).containsEntry("testTagNested", "NestedTagValue123")

    assertThat(miskExceptionLogs).hasSize(1)
    with(miskExceptionLogs.single()) {
      assertThat(throwableProxy.message).isEqualTo("Nested logger test exception")
      assertThat(message).contains("unexpected error dispatching to")
      assertThat(level).isEqualTo(Level.ERROR)
      assertThat(mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
      assertThat(mdcPropertyMap).containsEntry("testTagNested", "NestedTagValue123")
    }
  }

  class NestedTaggedLoggersThrowsException @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      return logger
        .testTag("SpecialTagValue123")
        .asContext {
          logger.info { "Non nested log message" }
          functionWithNestedTaggedLogger()
        }
    }

    private fun functionWithNestedTaggedLogger(): String {
      return logger
        .testTagNested("NestedTagValue123")
        .asContext {
          logger.info { "Nested log message with two mdc properties" }
          throw NestedTaggedLoggersException("Nested logger test exception")
        }
    }

    class NestedTaggedLoggersException(message: String) : Throwable(message)

    companion object {
      val logger = this::class.getTaggedLoggerNested()
      const val URL = "/log/NestedTaggedLoggersLogger/test"
    }

    data class ServiceExtendedTaggedLogger<L: Any>(val logClass: KClass<L>, val tags: Set<Tag> = emptySet()): TaggedLogger<L, ServiceExtendedTaggedLogger<L>>(logClass, tags),
    Copyable<ServiceExtendedTaggedLogger<L>> {
      fun testTag(value: String) = tag(Tag("testTag", value))
      fun testTagNested(value: String) = tag(Tag("testTagNested", value))

      companion object {
        fun <T : Any> KClass<T>.getTaggedLoggerNested(): ServiceExtendedTaggedLogger<T> {
          return ServiceExtendedTaggedLogger(this)
        }
      }

      override fun copyWithNewTags(newTags: Set<Tag>): ServiceExtendedTaggedLogger<L> {
        return this.copy(tags = newTags)
      }
    }
  }


  @Test
  fun shouldCorrectlyLogOuterMdcOnlyWhenNestedLoggerExceptionIsCaughtAndAnotherThrown() {
    val response = invoke(NestedLoggersOuterExceptionHandled.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(NestedLoggersOuterExceptionHandled::class, consumeUnmatchedLogs = false)
    val miskExceptionLogs = logCollector.takeEvents(ExceptionHandlingInterceptor::class)

    assertThat(serviceLogs).hasSize(1)
    assertThat(serviceLogs.single().message).isEqualTo("Exception caught and handled")
    assertThat(serviceLogs.single().level).isEqualTo(Level.WARN)
    assertThat(serviceLogs.single().mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
    assertThat(serviceLogs.single().mdcPropertyMap).doesNotContainKey("testTagNested")

    assertThat(miskExceptionLogs).hasSize(1)
    with(miskExceptionLogs.single()) {
      assertThat(throwableProxy.message).isEqualTo("Should not log MDC from nested tagged logger")
      assertThat(message).contains("unexpected error dispatching to")
      assertThat(level).isEqualTo(Level.ERROR)
      assertThat(mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
      assertThat(mdcPropertyMap).doesNotContainKey("testTagNested")
    }
  }

  class NestedLoggersOuterExceptionHandled @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      return logger
        .testTag("SpecialTagValue123")
        .asContext {
          try {
            functionWithNestedTaggedLogger()
          } catch (e: NestedTaggedLoggerException) {
            logger.warn { "Exception caught and handled" }
          }

          throw OuterTaggedLoggerException("Should not log MDC from nested tagged logger")
        }
    }

    private fun functionWithNestedTaggedLogger(): String {
      return logger
        .testTagNested("NestedTagValue123")
        .asContext {
          throw NestedTaggedLoggerException("Nested logger test exception")
        }
    }

    class NestedTaggedLoggerException(message: String) : Throwable(message)
    class OuterTaggedLoggerException(message: String) : Throwable(message)

    companion object {
      val logger = this::class.getTaggedLoggerNestedOuterExceptionThrown()
      const val URL = "/log/NestedLoggersOuterExceptionHandled/test"
    }

    data class ServiceExtendedTaggedLogger<L: Any>(val logClass: KClass<L>, val tags: Set<Tag> = emptySet()): TaggedLogger<L, ServiceExtendedTaggedLogger<L>>(logClass, tags),
      Copyable<ServiceExtendedTaggedLogger<L>> {
      fun testTag(value: String)= tag("testTag" to value)
      fun testTagNested(value: String) = tag("testTagNested" to value)

      companion object {
        fun <T : Any> KClass<T>.getTaggedLoggerNestedOuterExceptionThrown(): ServiceExtendedTaggedLogger<T> {
          return ServiceExtendedTaggedLogger(this)
        }
      }

      override fun copyWithNewTags(newTags: Set<Tag>): ServiceExtendedTaggedLogger<L> {
        return this.copy(tags = newTags)
      }
    }
  }


  @Test
  fun shouldCorrectlyResetThreadLocalWhenExceptionCaughtAndNoneThrown() {
    val response = invoke(NestedLoggersOuterExceptionHandledNoneThrown.URL, "caller")
    assertThat(response.code).isEqualTo(200)

    val serviceLogs = logCollector.takeEvents(NestedLoggersOuterExceptionHandledNoneThrown::class, consumeUnmatchedLogs = false)
    val miskExceptionLogs = logCollector.takeEvents(ExceptionHandlingInterceptor::class)

    assertThat(serviceLogs).hasSize(1)
    assertThat(serviceLogs.single().message).isEqualTo("Should be zero size and log with no MDC context: 0")
    assertThat(serviceLogs.single().level).isEqualTo(Level.INFO)
    assertThat(serviceLogs.single().mdcPropertyMap).doesNotContainKey("testTag")
    assertThat(serviceLogs.single().mdcPropertyMap).doesNotContainKey("testTagNested")

    assertThat(miskExceptionLogs).hasSize(0)
  }

  class NestedLoggersOuterExceptionHandledNoneThrown @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      logger
        .testTag("SpecialTagValue123")
        .asContext {
          try {
            functionWithNestedTaggedLogger()
          } catch (_: NestedTaggedLoggerException) {
            // Just squash for this test
          }
        }

      // This is testing the ThreadLocal cleanup function within TaggedLogger when asContext() exits
      // without throwing an exception
      val shouldBeEmptySet = TaggedLogger.popThreadLocalMdcContext()
      logger.info { "Should be zero size and log with no MDC context: ${shouldBeEmptySet.size}" }
      return ""
    }

    private fun functionWithNestedTaggedLogger(): String {
      return logger
        .testTagNested("NestedTagValue123")
        .asContext {
          throw NestedTaggedLoggerException("Nested logger test exception")
        }
    }

    class NestedTaggedLoggerException(message: String) : Throwable(message)
    class OuterTaggedLoggerException(message: String) : Throwable(message)

    companion object {
      val logger = this::class.getTaggedLoggerNestedOuterExceptionThrownThenNone()
      const val URL = "/log/NestedLoggersOuterExceptionHandledNoneThrown/test"
    }

    data class ServiceExtendedTaggedLogger<L: Any>(val logClass: KClass<L>, val tags: Set<Tag> = emptySet()): TaggedLogger<L, ServiceExtendedTaggedLogger<L>>(logClass, tags),
      Copyable<ServiceExtendedTaggedLogger<L>> {
      fun testTag(value: String)= tag("testTag" to value)
      fun testTagNested(value: String) = tag("testTagNested" to value)

      companion object {
        fun <T : Any> KClass<T>.getTaggedLoggerNestedOuterExceptionThrownThenNone(): ServiceExtendedTaggedLogger<T> {
          return ServiceExtendedTaggedLogger(this)
        }
      }

      override fun copyWithNewTags(newTags: Set<Tag>): ServiceExtendedTaggedLogger<L> {
        return this.copy(tags = newTags)
      }
    }
  }


  fun invoke(url: String, asService: String? = null): okhttp3.Response {
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath(url)
      .build()

    val request = okhttp3.Request.Builder()
      .url(url)
      .get()
    asService?.let { request.addHeader(FakeCallerAuthenticator.SERVICE_HEADER, it) }
    return httpClient.newCall(request.build()).execute()
  }
}
