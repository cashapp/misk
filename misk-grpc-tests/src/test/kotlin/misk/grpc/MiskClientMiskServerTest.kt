package misk.grpc

import com.google.inject.Guice
import com.google.inject.util.Modules
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import misk.grpc.miskclient.MiskGrpcClientModule
import misk.grpc.miskclient.RouteGuideCallCounter
import misk.grpc.miskserver.RouteChatGrpcAction
import misk.grpc.miskserver.RouteGuideMiskServiceModule
import misk.inject.getInstance
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.interceptors.RequestLoggingInterceptor
import okhttp3.HttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import routeguide.Feature
import routeguide.Point
import routeguide.RouteGuideClient
import routeguide.RouteNote
import wisp.logging.LogCollector

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
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).containsExactly(
      "GetFeatureGrpcAction principal=unknown time=0.000 ns code=200 " +
        "request=[Point{latitude=43, longitude=-80}] " +
        "response=Feature{name=maple tree, location=Point{latitude=43, longitude=-80}}"
    )
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
    assertThat(logCollector.takeMessages(RequestLoggingInterceptor::class)).containsExactly(
      "RouteChatGrpcAction principal=unknown time=0.000 ns code=200 " +
        "request=[GrpcMessageSource, GrpcMessageSink] response=kotlin.Unit"
    )
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
}
