package misk.grpc

import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import grpc.reflection.v1alpha.ServerReflectionClient
import grpc.reflection.v1alpha.ServerReflectionRequest
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.actions.WebAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import routeguide.Feature
import routeguide.Point
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@MiskTest(startService = true)
class GrpcReflectNoProtoFoundTest {
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(GrpcReflectTestingModule())
      install(WebActionModule.create<FakeGetFeatureBlockingServerAction>())
    }
  }

  @Inject lateinit var clientProvider: Provider<ServerReflectionClient>

  @Test
  fun `silent fail on missing proto file`() {
    val client = clientProvider.get()

    val call = client.ServerReflectionInfo()
    val (requests, responses) = call.executeBlocking()

    responses.use {
      requests.use {
        requests.write(
          ServerReflectionRequest(
            list_services = "*"
          )
        )

        val firstResponse = responses.read()
        assertThat(firstResponse!!.list_services_response!!.service).isNotEmpty()
      }
    }
  }

  /** Just an endpoint so we can have more sample data to reflect upon. */
  @Singleton
  private class FakeGetFeatureBlockingServerAction @Inject constructor(
  ) : FakeGetFeatureBlockingServer, WebAction {
    override fun GetFeature(request: Point): Feature = error("unsupported")
  }

  /** Synthesize a gRPC method with no source code. This could have been generated, but it's not. */
  interface FakeGetFeatureBlockingServer : Service {
    @WireRpc(
      path = "/routeguide.RouteGuide/GetFeature",
      requestAdapter = "routeguide.Point#ADAPTER",
      responseAdapter = "routeguide.Feature#ADAPTER",
      sourceFile = "fakerouteguide/RouteGuideProto.proto"
    )
    fun GetFeature(request: Point): Feature
  }
}
