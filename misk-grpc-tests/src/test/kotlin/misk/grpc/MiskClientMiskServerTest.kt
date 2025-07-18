package misk.grpc

import ch.qos.logback.classic.Level
import com.google.inject.Guice
import com.google.inject.util.Modules
import com.squareup.wire.GrpcException
import com.squareup.wire.GrpcStatus
import jakarta.inject.Inject
import jakarta.inject.Named
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.miskclient.RouteGuideCallCounter
import misk.grpc.miskserver.RouteChatGrpcAction
import misk.grpc.miskserver.RouteGuideMiskServiceModule
import misk.inject.getInstance
import misk.logging.LogCollectorModule
import misk.metrics.FakeMetrics
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.exceptions.ExceptionHandlingInterceptor
import misk.web.interceptors.RequestLoggingInterceptor
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.Durations.ONE_MILLISECOND
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.awaitility.kotlin.withPollInterval
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import routeguide.Feature
import routeguide.Point
import routeguide.RouteGuideClient
import routeguide.RouteNote
import misk.logging.LogCollector

@MiskTest(startService = true)
class MiskClientMiskServerTest {
  @MiskTestModule
  val module = Modules.combine(
    RouteGuideMiskServiceModule(),
    LogCollectorModule()
  )

  @Inject lateinit var logCollector: LogCollector
  @Inject lateinit var routeChatGrpcAction: RouteChatGrpcAction
  @Inject @field:Named("grpc server") lateinit var serverUrl: HttpUrl
  @Inject lateinit var metrics: FakeMetrics

  private lateinit var routeGuide: RouteGuideClient
  private lateinit var callCounter: RouteGuideCallCounter

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(MiskGrpcClientModule(serverUrl))
    routeGuide = clientInjector.getInstance()
    callCounter = clientInjector.getInstance()
  }

  @Test
  fun requestResponse() {
    val point = Point(
      latitude = 43,
      longitude = -80
    )
    val feature = Feature(
      name = "maple tree",
      location = point
    )

    runBlocking {
      val returnValue = routeGuide.GetFeature().execute(point)
      assertThat(returnValue).isEqualTo(feature)
    }

    // Confirm interceptors were invoked.
    val logs = logCollector.takeMessages(RequestLoggingInterceptor::class)
    assertEquals(1, logs.size)
    assertThat(logs[0])
      .contains("GetFeatureGrpcAction principal=unknown time=0.000 ns code=200")
      .contains("request=Point{latitude=43, longitude=-80}")
      // There could be additional headers (e.g. tests are run with tracing or javaagent)
      .contains("requestHeaders={accept-encoding=[gzip], content-type=[application/grpc]")
      .contains("response=Feature{name=maple tree, location=Point{latitude=43, longitude=-80}}")
      .contains("responseHeaders={}")
    assertThat(callCounter.counter("default.GetFeature").get()).isEqualTo(1)
    assertThat(callCounter.counter("default.RouteChat").get()).isEqualTo(0)
  }

  @Test
  fun duplexStreaming() {
    runBlocking {
      val (sendChannel, receiveChannel) = routeGuide.RouteChat().executeIn(GlobalScope)
      sendChannel.send(RouteNote(message = "a"))
      assertThat(receiveChannel.receive()).isEqualTo(RouteNote(message = "ACK: a"))
      sendChannel.send(RouteNote(message = "b"))
      assertThat(receiveChannel.receive()).isEqualTo(RouteNote(message = "ACK: b"))
      sendChannel.close()
    }

    // Confirm interceptors were invoked.
    val logs = logCollector.takeMessages(RequestLoggingInterceptor::class)
    assertEquals(1, logs.size)
    assertThat(logs[0])
      .contains("RouteChatGrpcAction principal=unknown time=0.000 ns code=200")
      .contains("request=[GrpcMessageSource, GrpcMessageSink]")
      // There could be additional headers (e.g. tests are run with tracing or javaagent)
      .contains("requestHeaders={accept-encoding=[gzip], content-type=[application/grpc]")
      .contains("response=kotlin.Unit")
      .contains("responseHeaders={content-type=[application/grpc]}")
    assertThat(callCounter.counter("default.GetFeature").get()).isEqualTo(0)
    assertThat(callCounter.counter("default.RouteChat").get()).isEqualTo(1)
  }

  @Test
  fun duplexStreamingResponseFirst() {
    routeChatGrpcAction.welcomeMessage = "welcome"

    runBlocking {
      val (sendChannel, receiveChannel: ReceiveChannel<RouteNote>) =
        routeGuide.RouteChat().executeIn(GlobalScope)
      assertThat(receiveChannel.receive()).isEqualTo(RouteNote(message = "welcome"))
      sendChannel.close()
    }
  }

  @Test
  fun serverFailureGeneric() {
    val point = Point(
      latitude = -1,
      longitude = 500
    )

    runBlocking {
      val e = assertFailsWith<GrpcException> {
        routeGuide.GetFeature().execute(point)
      }
      assertThat(e.grpcMessage).isEqualTo("unexpected latitude error!")
      assertThat(e.grpcStatus).isEqualTo(GrpcStatus.UNKNOWN)

      // Assert that _metrics_ counted a 500 and no 200s, even though an HTTP 200 was returned
      // over HTTP. The 200 is implicitly asserted by the fact that we got a GrpcException, which
      // is only thrown if a properly constructed gRPC error is received.
      assertResponseCount(200, 0)
      assertResponseCount(500, 1)

      // Confirm only RequestLoggingInterceptor logs exceptions
      val requestLoggingInterceptorLogs = logCollector.takeMessages(RequestLoggingInterceptor::class, Level.ERROR)
      assertEquals(1, requestLoggingInterceptorLogs.size)
      assertThat(requestLoggingInterceptorLogs[0])
        .contains("GetFeatureGrpcAction principal=unknown time=0.000 ns failed request=Point{latitude=-1, longitude=500}")

      val exceptionLoggingInterceptorLogs = logCollector.takeMessages(ExceptionHandlingInterceptor::class)
      assertThat(exceptionLoggingInterceptorLogs).isEmpty()
    }
  }

  @Test
  fun serverFailureNotFound() {
    val point = Point(
      latitude = -1,
      longitude = 404
    )

    runBlocking {
      val e = assertFailsWith<GrpcException> {
        routeGuide.GetFeature().execute(point)
      }
      assertThat(e.grpcMessage).isEqualTo("unexpected latitude error!")
      assertThat(e.grpcStatus).isEqualTo(GrpcStatus.NOT_FOUND)
        .withFailMessage("wrong gRPC status ${e.grpcStatus.name}")

      // Assert that _metrics_ counted a 404 and no 200s, even though an HTTP 200 was returned
      // over HTTP. The 200 is implicitly asserted by the fact that we got a GrpcException, which
      // is only thrown if a properly constructed gRPC error is received.
      assertResponseCount(200, 0)
      assertResponseCount(404, 1)

      // Confirm only RequestLoggingInterceptor logs exceptions
      val requestLoggingInterceptorLogs = logCollector.takeMessages(RequestLoggingInterceptor::class, Level.WARN)
      assertEquals(1, requestLoggingInterceptorLogs.size)
      assertThat(requestLoggingInterceptorLogs[0])
        .contains("GetFeatureGrpcAction principal=unknown time=0.000 ns failed request=Point{latitude=-1, longitude=404}")

      val exceptionLoggingInterceptorLogs = logCollector.takeMessages(ExceptionHandlingInterceptor::class)
      assertThat(exceptionLoggingInterceptorLogs).isEmpty()
    }
  }

  private fun assertResponseCount(code: Int, count: Int) {
    await withPollInterval ONE_MILLISECOND atMost ONE_HUNDRED_MILLISECONDS untilCallTo {
      metrics.histogramCount(
        "http_request_latency_ms",
        "action" to "GetFeatureGrpcAction",
        "caller" to "unknown",
        "code" to code.toString(),
      )?.toInt() ?: 0
    } matches { it == count }
  }

  @Test
  fun grpcStatusError() {
    val point = Point(
      latitude = -91,
      longitude = 10,
    )

    runBlocking {
      val e = assertFailsWith<GrpcException> {
        routeGuide.GetFeature().execute(point)
      }
      assertThat(e.grpcMessage).isEqualTo("invalid coordinates")
      assertThat(e.grpcStatus).isEqualTo(GrpcStatus.INVALID_ARGUMENT)
        .withFailMessage("wrong gRPC status ${e.grpcStatus.name}")

      // Assert that _metrics_ counted a HTTP_BAD_REQUEST and no 200s, even though an HTTP 200 was
      // returned over HTTP. The 200 is implicitly asserted by the fact that we got a GrpcException,
      // which is only thrown if a properly constructed gRPC error is received.
      assertResponseCount(200, 0)
      assertResponseCount(HTTP_BAD_REQUEST, 1)
    }
  }

}
