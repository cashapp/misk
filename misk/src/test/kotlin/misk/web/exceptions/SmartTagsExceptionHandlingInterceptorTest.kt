@file:OptIn(ExperimentalMiskApi::class)

package misk.web.exceptions

import ch.qos.logback.classic.Level
import com.google.common.testing.FakeTicker
import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.annotation.ExperimentalMiskApi
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
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
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import mu.KLogger
import mu.KotlinLogging
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import wisp.logging.LogCollector
import wisp.logging.SmartTagsThreadLocalHandler
import wisp.logging.Tag
import wisp.logging.getLogger
import wisp.logging.info
import wisp.logging.withSmartTags
import wisp.logging.withTags
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

@MiskTest(startService = true)
internal class SmartTagsExceptionHandlingInterceptorTest {
  @MiskTestModule
  val module = object :KAbstractModule() {
    override fun configure() {
      install(LogCollectorModule())
      install(AccessControlModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()

      install(WebActionModule.create<ServiceNotUsingSmartTagsShouldLogWithHandRolledMdcContext>())
      install(WebActionModule.create<ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext>())
      install(WebActionModule.create<LogMDCContextTestAction>())
      install(WebActionModule.create<NestedSmartTagsThrowsException>())
      install(WebActionModule.create<NestedWithTagsAndWithSmartTagsThrowsException>())
      install(WebActionModule.create<NestedWithSmartTagsAndWithTagsThrowsException>())
      install(WebActionModule.create<NestedLoggersOuterExceptionHandled>())
      install(WebActionModule.create<NestedLoggersOuterExceptionHandledNoneThrown>())
      install(WebActionModule.create<NestedSmartTagsBothSucceed>())
      install(WebActionModule.create<DetectWrappedExceptionsUsingCausedBy>())
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
  fun serviceNotUsingSmartTagsShouldLogWithMdcContext() {
    val response = invoke(ServiceNotUsingSmartTagsShouldLogWithHandRolledMdcContext.URL, "caller")
    assertThat(response.code).isEqualTo(200)

    val logs =
      logCollector.takeEvents(ServiceNotUsingSmartTagsShouldLogWithHandRolledMdcContext::class)
    assertThat(logs).hasSize(2)

    assertThat(logs.first().message).isEqualTo("Start Test Process")
    assertThat(logs.first().level).isEqualTo(Level.INFO)
    assertThat(logs.first().mdcPropertyMap).containsEntry("HandRolledLoggerTag", "handRolledLoggerTagValue")
    assertThat(logs.first().mdcPropertyMap).containsEntry("secondHandRolledTag", "secondHandRolledTagValue")

    assertThat(logs.last().message).isEqualTo("Log message using withTags")
    assertThat(logs.last().level).isEqualTo(Level.WARN)
    assertThat(logs.last().mdcPropertyMap).containsEntry("withTagsTag1", "withTagsValue1")
    assertThat(logs.last().mdcPropertyMap).containsEntry("withTagsTag2", "withTagsValue2")
  }

  class ServiceNotUsingSmartTagsShouldLogWithHandRolledMdcContext @Inject constructor() :
    WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      logger.info(
        Tag("HandRolledLoggerTag", "handRolledLoggerTagValue"),
        "secondHandRolledTag" to "secondHandRolledTagValue"
      ) { "Start Test Process" }

      withTags(
        Tag("withTagsTag1", "withTagsValue1"),
        "withTagsTag2" to "withTagsValue2"
      ) {
        logger.warn { "Log message using withTags" }
      }
      return "value"
    }

    companion object {
      val logger = getLogger<ServiceNotUsingSmartTagsShouldLogWithHandRolledMdcContext>()
      const val URL = "/log/ServiceNotUsingSmartTagsShouldLogWithHandRolledMdcContext/test"
    }
  }


  // This test verifies the current mdc log tag approach still used in a lot of services works as expected
  @Test
  fun serviceNotUsingSmartTagsShouldLogExceptionWithoutMdcContext() {
    val response = invoke(ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(
      ServiceUsingLegacyTaggedLoggerShouldLogExceptionWithoutMdcContext::class, consumeUnmatchedLogs = false)
    val miskLogs = logCollector.takeEvents(ExceptionHandlingInterceptor::class)

    // The service info log had a legacy style SmartTags defined within the service using asContext
    // This will log regular messages with the specified mdc tags
    assertThat(serviceLogs).hasSize(1)
    with(serviceLogs.single()) {
      assertThat(message).isEqualTo("Test log with tags")
      assertThat(level).isEqualTo(Level.INFO)
      assertThat(mdcPropertyMap).containsEntry("tag123", "value123")
      assertThat(mdcPropertyMap).containsEntry("secondTag123", "secondValue123")
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
        .tag(Tag("secondTag123", "secondValue123"))
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
      assertThat(mdcPropertyMap).containsEntry("secondTag", "SecondTagValue123")
    }
  }

  class LogMDCContextTestAction @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      withSmartTags(
        "testTag" to "SpecialTagValue123",
        "secondTag" to "SecondTagValue123"
      ) {
        logger.info { "Tester" }
        throw LogMDCContextTestActionException("Test Exception")
      }
    }

    class LogMDCContextTestActionException(message: String) : Exception(message)

    companion object {
      val logger = getLogger<LogMDCContextTestAction>()
      const val URL = "/log/LogMDCContextTestAction/test"
    }
  }

  @Test
  fun shouldHandleConcurrentThreadsUsingSmartTags() {
    // Start one instance of the service
    val url = jettyService.httpServerUrl.newBuilder()
      .encodedPath(NestedSmartTagsBothSucceed.URL)
      .build()

    // Setup callable to call that service and get the response
    // Need to identify the caller by a unique ID to filter logs
    val callService: (String) -> Callable<String> = { identifier ->
      Callable {
        val request = okhttp3.Request.Builder()
          .url(url)
          .get()
          .addHeader(NestedSmartTagsBothSucceed.IDENTIFIER_HEADER, identifier)

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
      .takeEvents(NestedSmartTagsBothSucceed::class, consumeUnmatchedLogs = false)
      .plus(logCollector.takeEvents(NestedSmartTagsBothSucceed.AnotherClass::class, consumeUnmatchedLogs = false))

    // Deterministically order all messages
    val serviceLogsOrdered = serviceLogs.filter { it.message == "Non nested log message" }
      .plus(serviceLogs.filter { it.message == "Nested log message with two mdc properties" })
      .plus(serviceLogs.filter { it.message == "Non nested log message after nest" })
      .plus(serviceLogs.filter { it.message == "Log message after SmartTags" })

    val miskExceptionLogs = logCollector.takeEvents(ExceptionHandlingInterceptor::class)

    serviceLogsOrdered.groupBy { it.mdcPropertyMap[NestedSmartTagsBothSucceed.EXECUTION_IDENTIFIER] }
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

        assertThat(logs.last().message).isEqualTo("Log message after SmartTags")
        assertThat(logs.last().mdcPropertyMap).doesNotContainKey("testTag")
        assertThat(logs.last().mdcPropertyMap).doesNotContainKey("testTagNested")
      }

    assertThat(miskExceptionLogs).hasSize(0)
    println("Done")
  }

  class NestedSmartTagsBothSucceed @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(@RequestHeaders headers: Headers): String {
      val result = withSmartTags(
        "testTag" to "SpecialTagValue123",
        EXECUTION_IDENTIFIER to headers[IDENTIFIER_HEADER]
      ) {
        logger.info { "Non nested log message" }
        var result = "NO RESULT"

        // Starting a new thread will not inherit the MDC context by default due to MDC being thread local
        Thread {
          result = AnotherClass().functionWithNestedSmartTags(headers[IDENTIFIER_HEADER])
        }.also { it.start() }.join(1000)

        logger.info { "Non nested log message after nest" }
        result
      }

      // Manually add this tag to identify the execution for verification
      logger.info(EXECUTION_IDENTIFIER to headers[IDENTIFIER_HEADER]) { "Log message after SmartTags" }
      return result
    }

    companion object {
      val logger = getLogger<NestedSmartTagsBothSucceed>()
      const val URL = "/log/NestedSmartTagsBothSucceed/test"
      const val IDENTIFIER_HEADER = "IDENTIFIER_HEADER"
      const val EXECUTION_IDENTIFIER = "executionIdentifier"
    }

    class AnotherClass() {
      fun functionWithNestedSmartTags(parentTag: String?): String {
        return withSmartTags(
          EXECUTION_IDENTIFIER to parentTag,
          "testTagNested" to "NestedTagValue123"
        ) {
          logger.info { "Nested log message with two mdc properties" }
          "SUCCESS"
        }
      }

      companion object {
        val logger = getLogger<AnotherClass>()
      }
    }
  }

  @Test
  fun shouldHaveLogAndExceptionsFromServiceWithNestedMdcContext() {
    val response = invoke(NestedSmartTagsThrowsException.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(NestedSmartTagsThrowsException::class, consumeUnmatchedLogs = false)
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

  class NestedSmartTagsThrowsException @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      return withSmartTags("testTag" to "SpecialTagValue123") {
        logger.info { "Non nested log message" }
        functionWithNestedSmartTags()
      }
    }

    private fun functionWithNestedSmartTags(): String {
      return withSmartTags("testTagNested" to "NestedTagValue123") {
        logger.info { "Nested log message with two mdc properties" }
        throw NestedSmartTagsException("Nested logger test exception")
      }
    }

    class NestedSmartTagsException(message: String) : Exception(message)

    companion object {
      val logger = getLogger<NestedSmartTagsThrowsException>()
      const val URL = "/log/NestedSmartTagsLogger/test"
    }
  }

  @Test
  fun shouldHandleMixedUseOfWithTagsAndWithSmartTags() {
    val response = invoke(NestedWithTagsAndWithSmartTagsThrowsException.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(NestedWithTagsAndWithSmartTagsThrowsException::class, consumeUnmatchedLogs = false)
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
      assertThat(mdcPropertyMap).doesNotContainKey("testTag")
      assertThat(mdcPropertyMap).containsEntry("testTagNested", "NestedTagValue123")
    }
  }

  /*
  When outer nesting is withTags and inner nesting is withSmartTags and the inner nesting throws an exception,
  the tags on the exception log should only contain the inner tags and not the outer tags.
   */
  class NestedWithTagsAndWithSmartTagsThrowsException @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      return withTags("testTag" to "SpecialTagValue123", includeTagsOnExceptionLogs = false) {
        logger.info { "Non nested log message" }
        functionWithNestedSmartTags()
      }
    }

    private fun functionWithNestedSmartTags(): String {
      return withTags("testTagNested" to "NestedTagValue123", includeTagsOnExceptionLogs = true) { // aka "withSmartTags"
        logger.info { "Nested log message with two mdc properties" }
        throw NestedSmartTagsException("Nested logger test exception")
      }
    }

    class NestedSmartTagsException(message: String) : Exception(message)

    companion object {
      val logger = getLogger<NestedWithTagsAndWithSmartTagsThrowsException>()
      const val URL = "/log/NestedWithTagsAndWithSmartTagsThrowsException/test"
    }
  }

  @Test
  fun shouldHandleMixedUseOfWithSmartTagsAndWithTags() {
    val response = invoke(NestedWithSmartTagsAndWithTagsThrowsException.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(NestedWithSmartTagsAndWithTagsThrowsException::class, consumeUnmatchedLogs = false)
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
      assertThat(mdcPropertyMap).doesNotContainKey("testTagNested")
    }
  }

  /*
  When outer nesting is smartTags and inner nesting is withTags and the inner nesting throws an exception,
  the tags on the exception log should only contain the outer tags and not the inner tags.
   */
  class NestedWithSmartTagsAndWithTagsThrowsException @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      return withTags("testTag" to "SpecialTagValue123", includeTagsOnExceptionLogs = true) { // aka "withSmartTags"
        logger.info { "Non nested log message" }
        functionWithNestedSmartTags()
      }
    }

    private fun functionWithNestedSmartTags(): String {
      return withTags("testTagNested" to "NestedTagValue123", includeTagsOnExceptionLogs = false) {
        logger.info { "Nested log message with two mdc properties" }
        throw NestedSmartTagsException("Nested logger test exception")
      }
    }

    class NestedSmartTagsException(message: String) : Exception(message)

    companion object {
      val logger = getLogger<NestedWithSmartTagsAndWithTagsThrowsException>()
      const val URL = "/log/NestedWithSmartTagsAndWithTagsThrowsException/test"
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
      return withSmartTags("testTag" to "SpecialTagValue123") {
          try {
            functionWithNestedSmartTags()
          } catch (e: NestedSmartTagsException) {
            logger.warn { "Exception caught and handled" }
          }

          throw OuterSmartTagsException("Should not log MDC from nested tagged logger")
        }
    }

    private fun functionWithNestedSmartTags(): String {
      return withSmartTags("testTagNested" to "NestedTagValue123") {
          throw NestedSmartTagsException("Nested logger test exception")
        }
    }

    class NestedSmartTagsException(message: String) : Exception(message)
    class OuterSmartTagsException(message: String) : Exception(message)

    companion object {
      val logger = getLogger<NestedLoggersOuterExceptionHandled>()
      const val URL = "/log/NestedLoggersOuterExceptionHandled/test"
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
      withSmartTags("testTag" to "SpecialTagValue123") {
          try {
            functionWithNestedSmartTags()
          } catch (_: NestedSmartTagsException) {
            // Just squash for this test
          }
        }

      // This is testing the ThreadLocal cleanup function within SmartTags when asContext() exits
      // without throwing an exception
      val shouldBeEmptySet = SmartTagsThreadLocalHandler.popThreadLocalSmartTags()
      logger.info { "Should be zero size and log with no MDC context: ${shouldBeEmptySet.size}" }
      return ""
    }

    private fun functionWithNestedSmartTags(): String {
      return withSmartTags("testTag" to "SpecialTagValue123") {
          throw NestedSmartTagsException("Nested logger test exception")
        }
    }

    class NestedSmartTagsException(message: String) : Exception(message)
    class OuterSmartTagsException(message: String) : Exception(message)

    companion object {
      val logger = getLogger<NestedLoggersOuterExceptionHandledNoneThrown>()
      const val URL = "/log/NestedLoggersOuterExceptionHandledNoneThrown/test"
    }
  }

  @Test
  fun shouldDetectWrappedExceptionsUsingCausedByWhenSettingMdcTags() {
    val response = invoke(DetectWrappedExceptionsUsingCausedBy.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(DetectWrappedExceptionsUsingCausedBy::class, consumeUnmatchedLogs = false)
    val miskExceptionLogs = logCollector.takeEvents(ExceptionHandlingInterceptor::class)

    assertThat(serviceLogs).hasSize(2)
    assertThat(serviceLogs.first().message).isEqualTo("Non nested log message")
    assertThat(serviceLogs.first().mdcPropertyMap).containsEntry("outerNestedTag", "outerNestedTagValue")
    assertThat(serviceLogs.first().mdcPropertyMap).doesNotContainKey("innerNestedTag")

    assertThat(serviceLogs.last().message).isEqualTo("Nested log message with two mdc properties")
    assertThat(serviceLogs.last().mdcPropertyMap).containsEntry("outerNestedTag", "outerNestedTagValue")
    assertThat(serviceLogs.last().mdcPropertyMap).containsEntry("innerNestedTag", "innerNestedTagValue")

    assertThat(miskExceptionLogs).hasSize(1)
    with(miskExceptionLogs.single()) {
      assertThat(throwableProxy.message).isEqualTo("Wrapped by another exception")
      assertThat(message).contains("unexpected error dispatching to")
      assertThat(level).isEqualTo(Level.ERROR)
      assertThat(mdcPropertyMap).containsEntry("outerNestedTag", "outerNestedTagValue")
      assertThat(mdcPropertyMap).containsEntry("innerNestedTag", "innerNestedTagValue")
    }
  }

  class DetectWrappedExceptionsUsingCausedBy @Inject constructor() : WebAction {
    @Get(URL)
    @Unauthenticated
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun call(): String {
      return withSmartTags("outerNestedTag" to "outerNestedTagValue") {
        logger.info { "Non nested log message" }
        functionWithNestedSmartTagsWrapped()
      }
    }

    private fun functionWithNestedSmartTagsWrapped(): String {
      try {
        try {
          withSmartTags("innerNestedTag" to "innerNestedTagValue") {
            logger.info { "Nested log message with two mdc properties" }
            throw ProcessException("Nested logger test exception")
          }
        } catch (e: Exception) {
          throw WrappedByException("Wrapped by exception", e)
        }
      } catch (e: Exception) {
        throw WrappedByAnotherException("Wrapped by another exception", e)
      }
    }

    class ProcessException(message: String) : Exception(message)
    class WrappedByException(message: String, e: Exception) : Exception(message, e)
    class WrappedByAnotherException(message: String, e: Exception) : Exception(message, e)

    companion object {
      val logger = getLogger<DetectWrappedExceptionsUsingCausedBy>()
      const val URL = "/log/DetectWrappedExceptionsUsingCausedBy/test"
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
