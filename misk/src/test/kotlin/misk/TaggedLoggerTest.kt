package misk

import ch.qos.logback.classic.Level
import com.google.common.testing.FakeTicker
import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.random.FakeRandom
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
import misk.web.WebTestClient
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import wisp.logging.Tag
import wisp.logging.getLogger
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

@MiskTest(startService = true)
internal class RequestLogContextInterceptorTest {
  @MiskTestModule
  val module = TestModule()
  val httpClient = OkHttpClient()

  @Inject private lateinit var moshi: Moshi

  @Inject private lateinit var webTestClient: WebTestClient
  @Inject private lateinit var jettyService: JettyService
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var fakeTicker: FakeTicker
  @Inject private lateinit var fakeRandom: FakeRandom

  @BeforeEach
  fun setUp() {
    fakeTicker.setAutoIncrementStep(100L, TimeUnit.MILLISECONDS)
  }

  @Test
  fun shouldLogFromServiceWithoutMdcContext() {
    val response = invoke(ServiceNotUsingTaggedLoggerShouldLogWithoutMdcContext.URL, "caller")
    assertThat(response.code).isEqualTo(200)

    val logs = logCollector.takeEvents(ServiceNotUsingTaggedLoggerShouldLogWithoutMdcContext::class)
    assertThat(logs).hasSize(1)
    assertThat(logs.first().mdcPropertyMap).doesNotContainKey("testTag")
  }

  @Test
  fun shouldLogFromServiceWithMdcContext() {
    val response = invoke(ServiceUsingTaggedLoggerShouldLogWithMdcContext.URL, "caller")
    assertThat(response.code).isEqualTo(200)

    val logs = logCollector.takeEvents(ServiceUsingTaggedLoggerShouldLogWithMdcContext::class)
    assertThat(logs).hasSize(1)
    assertThat(logs.first().mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
  }

  @Test
  fun shouldLogExceptionWithoutMdcContextWhenThrownByImplementingServiceWithoutTaggedLogger() {
    val response = invoke(ServiceNotUsingTaggedLoggerThrowsException.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val logs = logCollector.takeEvents()
    val logMessages = logs.map { it.message }

    assertThat(logs).hasSizeGreaterThanOrEqualTo(2)

    assertThat(logs).anyMatch { it.message == "Start Test Process" }
    assertThat(logs).anyMatch { it.message == "unexpected error dispatching to ServiceNotUsingTaggedLoggerThrowsException" }

    val inServiceStartProcessLogEvent = logs.single { it.message == "Start Test Process" }
    assertThat(inServiceStartProcessLogEvent).matches {
      it.loggerName == "misk.web.exceptions.ServiceNotUsingTaggedLoggerThrowsException" &&
        it.level == Level.INFO
    }
    assertThat(inServiceStartProcessLogEvent.mdcPropertyMap).doesNotContainKey("testTag")

    val exceptionLogEvent = logs.single() { it.message == "unexpected error dispatching to ServiceNotUsingTaggedLoggerThrowsException" }
    assertThat(exceptionLogEvent).matches {
      it.loggerName == "misk.web.exceptions.ExceptionHandlingInterceptor" &&
        it.level == Level.INFO
    }
    assertThat(inServiceStartProcessLogEvent.mdcPropertyMap).doesNotContainKey("testTag")
  }

  @Test
  fun shouldLogExceptionWithMdcContextWhenThrownByImplementingServiceWithTaggedLogger() {
    val response = invoke(ServiceUsingTaggedLoggerThrowsException.URL, "caller")
    assertThat(response.code).isEqualTo(500)

    val logs = logCollector.takeEvents()
    val logMessages = logs.map { it.message }

    assertThat(logs).hasSizeGreaterThanOrEqualTo(2)

    assertThat(logs).anyMatch { it.message == "Start Test Process" }
    assertThat(logs).anyMatch { it.message == "unexpected error dispatching to ServiceUsingTaggedLoggerThrowsException" }

    val inServiceStartProcessLogEvent = logs.single { it.message == "Start Test Process" }
    assertThat(inServiceStartProcessLogEvent).matches {
      it.loggerName == "misk.web.exceptions.ServiceUsingTaggedLoggerThrowsException" &&
        it.level == Level.INFO
    }
    assertThat(inServiceStartProcessLogEvent.mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")

    val exceptionLogEvent = logs.single() { it.message == "unexpected error dispatching to ServiceUsingTaggedLoggerThrowsException" }
    assertThat(exceptionLogEvent).matches {
      it.loggerName == "misk.web.exceptions.ExceptionHandlingInterceptor" &&
        it.level == Level.INFO
    }
    assertThat(inServiceStartProcessLogEvent.mdcPropertyMap).containsEntry("testTag", "SpecialTagValue123")
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

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(LogCollectorModule())
      install(AccessControlModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
      install(WebActionModule.create<ServiceNotUsingTaggedLoggerShouldLogWithoutMdcContext>())
      install(WebActionModule.create<ServiceUsingTaggedLoggerShouldLogWithMdcContext>())
      install(WebActionModule.create<ServiceNotUsingTaggedLoggerThrowsException>())
    }
  }
}

internal class ServiceNotUsingTaggedLoggerShouldLogWithoutMdcContext @Inject constructor() : WebAction {
  @Get(URL)
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun call(): String {
    logger.info { "Start Test Process" }
    return "Value"
  }

  companion object {
    val logger = getLogger<ServiceNotUsingTaggedLoggerShouldLogWithoutMdcContext>()
    const val URL = "/log/ServiceNotUsingTaggedLoggerShouldLogWithoutMdcContext/test"
  }
}

internal class ServiceUsingTaggedLoggerShouldLogWithMdcContext @Inject constructor() : WebAction {
  @Get(URL)
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun call(): String {
    logger
      .testTag("SpecialTagValue123")
      .asContext {
        logger.info { "Tester" }
      }
    return "LogMDCContextTestAction - done"
  }

  companion object {
    val logger = LogMDCContextTestActionLogger(ServiceUsingTaggedLoggerShouldLogWithMdcContext::class)
    const val URL = "/log/LogMDCContextTestAction/test"
  }

  class LogMDCContextTestActionLogger<L: Any>(logClass: KClass<L>): TaggedLogger<L>(logClass) {
    fun testTag(value: String): LogMDCContextTestActionLogger<L> {
      tag(Tag("testTag", value))
      return this
    }
  }
}

internal class ServiceNotUsingTaggedLoggerThrowsException @Inject constructor() : WebAction {
  @Get(URL)
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun call(): String {
    logger.info { "Start Test Process" }
    throw ServiceNotUsingTaggedLoggerException("Exception test message")
  }

  companion object {
    val logger = getLogger<ServiceNotUsingTaggedLoggerThrowsException>()
    const val URL = "/log/ServiceNotUsingTaggedLoggerThrowsException/test"
  }

  class ServiceNotUsingTaggedLoggerException(message: String): Throwable(message)
}

internal class ServiceUsingTaggedLoggerThrowsException @Inject constructor() : WebAction {
  @Get(URL)
  @Unauthenticated
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun call(): String {
    logger
      .testTag("SpecialTagValue123")
      .asContext {
        logger.info { "Tester" }
        throw ServiceUsingTaggedLoggerException("ServiceUsingTaggedLogger exception")
      }
    return "LogMDCContextTestAction - done"
  }

  companion object {
    val logger = LogMDCContextTestActionLogger(ServiceUsingTaggedLoggerShouldLogWithMdcContext::class)
    const val URL = "/log/LogMDCContextTestAction/test"
  }

  class LogMDCContextTestActionLogger<L: Any>(logClass: KClass<L>): TaggedLogger<L>(logClass) {
    fun testTag(value: String): LogMDCContextTestActionLogger<L> {
      tag(Tag("testTag", value))
      return this
    }
  }

  class ServiceUsingTaggedLoggerException(message: String): Throwable(message)
}

