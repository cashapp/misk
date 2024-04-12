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
import misk.web.exceptions.TaggedLoggerExceptionHandlingInterceptorTest.NestedTaggedLoggers.ServiceExtendedTaggedLogger.Companion.getTaggedLoggerNested
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import mu.KLogger
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import wisp.logging.LogCollector
import wisp.logging.Tag
import wisp.logging.TaggedLogger
import wisp.logging.getLogger
import wisp.logging.info
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
      install(WebActionModule.create<NestedTaggedLoggers>())
      install(WebActionModule.create<NestedLoggersOuterExceptionHandled>())
      install(WebActionModule.create<NestedLoggersOuterExceptionHandledNoneThrown>())
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

    class LogMDCContextTestActionLogger<L: Any>(logClass: KClass<L>): TaggedLogger<L, LogMDCContextTestActionLogger<L>>(logClass) {
      fun testTag(value: String) = tag(Tag("testTag", value))

      companion object {
        fun <T : Any> KClass<T>.getTaggedLogger(): LogMDCContextTestActionLogger<T> {
          return LogMDCContextTestActionLogger(this)
        }
      }
    }
  }


  @Test
  fun shouldHaveLogAndExceptionsFromServiceWithNestedMdcContext() {
    val response = invoke(NestedTaggedLoggers.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val serviceLogs = logCollector.takeEvents(NestedTaggedLoggers::class, consumeUnmatchedLogs = false)
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

  class NestedTaggedLoggers @Inject constructor() : WebAction {
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

    class ServiceExtendedTaggedLogger<L: Any>(logClass: KClass<L>): TaggedLogger<L, ServiceExtendedTaggedLogger<L>>(logClass) {
      fun testTag(value: String) = tag(Tag("testTag", value))
      fun testTagNested(value: String) = tag(Tag("testTagNested", value))

      companion object {
        fun <T : Any> KClass<T>.getTaggedLoggerNested(): ServiceExtendedTaggedLogger<T> {
          return ServiceExtendedTaggedLogger(this)
        }
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

    class ServiceExtendedTaggedLogger<L: Any>(logClass: KClass<L>): TaggedLogger<L, ServiceExtendedTaggedLogger<L>>(logClass) {
      fun testTag(value: String)= tag("testTag" to value)
      fun testTagNested(value: String) = tag("testTagNested" to value)

      companion object {
        fun <T : Any> KClass<T>.getTaggedLoggerNestedOuterExceptionThrown(): ServiceExtendedTaggedLogger<T> {
          return ServiceExtendedTaggedLogger(this)
        }
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
      val shouldBeEmptySet = TaggedLogger.getThreadLocalMdcContext()
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

    class ServiceExtendedTaggedLogger<L: Any>(logClass: KClass<L>): TaggedLogger<L, ServiceExtendedTaggedLogger<L>>(logClass) {
      fun testTag(value: String)= tag("testTag" to value)
      fun testTagNested(value: String) = tag("testTagNested" to value)

      companion object {
        fun <T : Any> KClass<T>.getTaggedLoggerNestedOuterExceptionThrownThenNone(): ServiceExtendedTaggedLogger<T> {
          return ServiceExtendedTaggedLogger(this)
        }
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
