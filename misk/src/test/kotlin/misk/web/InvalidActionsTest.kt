package misk.web

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.ProvisionException
import com.squareup.protos.test.grpc.GreeterSayHelloBlockingServer
import com.squareup.protos.test.grpc.HelloReply
import com.squareup.protos.test.grpc.HelloRequest
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Duration
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.web.actions.WebAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import misk.web.mediatype.MediaTypes

class InvalidActionsTest {
  @Test
  fun failIdenticalActions() {
    val exception =
      assertThrows<IllegalStateException>("Should throw an exception") {
        Guice.createInjector(IdenticalActionsModule())
          .getInstance(ServiceManager::class.java)
          .startAsync()
          .awaitHealthy(Duration.ofSeconds(5))
      }
    assertThat(exception.message)
      .contains(
        "Actions [InvalidActionsTest.SomeAction, InvalidActionsTest.IdenticalAction] have identical routing annotations."
      )
  }

  class IdenticalActionsModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<SomeAction>())
      install(WebActionModule.create<IdenticalAction>())
    }
  }

  class SomeAction @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("application/json")
    @ResponseContentType("application/json")
    fun hello() = "hello"
  }

  class IdenticalAction @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("application/json")
    @ResponseContentType("application/json")
    fun hello() = "hello"
  }

  // TODO (r3mariano): Fail with errors for real.
  @Disabled
  @Test
  fun failGrpcWithHttp2Disabled() {
    val exception =
      assertThrows<ProvisionException>("Should throw an exception") {
        Guice.createInjector(GrpcActionsModule()).getInstance(ServiceManager::class.java).startAsync().awaitHealthy()
      }
    assertThat(exception.message).contains("HTTP/2 must be enabled if any gRPC actions are bound.")
  }

  class GrpcActionsModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule(webConfig = WebServerTestingModule.TESTING_WEB_CONFIG.copy(http2 = false)))
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloRpcAction>())
    }
  }

  @Singleton
  class HelloRpcAction @Inject constructor() : WebAction, GreeterSayHello {
    override fun sayHello(request: HelloRequest): HelloReply {
      return HelloReply.Builder().message("howdy, ${request.name}").build()
    }
  }

  interface GreeterSayHello : Service {
    @WireRpc(
      path = "/helloworld.Greeter/SayHello",
      requestAdapter = "com.squareup.protos.test.grpc.HelloRequest#ADAPTER",
      responseAdapter = "com.squareup.protos.test.grpc.HelloReply#ADAPTER",
    )
    fun sayHello(request: HelloRequest): HelloReply
  }

  @Test fun failEnableUnframedRequestsGrpcWithPostProtobufAction() {
    val exception = assertThrows<IllegalStateException>("Should throw an exception") {
      Guice.createInjector(UnframedGrpcModule()).getInstance(ServiceManager::class.java)
        .startAsync().awaitHealthy(Duration.ofSeconds(5))
    }
    assertThat(exception.message).contains("Actions [InvalidActionsTest.UnframedHelloRpcAction, InvalidActionsTest.UnframedHelloRpcAction] have identical routing annotations.")
  }

  class UnframedGrpcModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<UnframedHelloRpcAction>())
    }
  }

  @Singleton
  @Suppress("TestFunctionName", "unused")
  class UnframedHelloRpcAction @Inject constructor() : GreeterSayHelloBlockingServer, WebAction {

    @EnableUnframedRequests
    override fun SayHello(request: HelloRequest): HelloReply = HelloReply.Builder()
      .message("howdy, ${request.name}")
      .build()

    @Post("/helloworld.Greeter/SayHello")
    @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
    @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
    fun sayHelloProtobufOverHttp(@RequestBody request: HelloRequest) = SayHello(request)
  }

}
