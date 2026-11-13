package misk.grpc

import com.google.inject.Provider
import grpc.reflection.v1alpha.ServerReflectionClient
import grpc.reflection.v1alpha.ServerReflectionRequest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.actions.WebAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import transitive.EchoRequest
import transitive.EchoResponse
import transitive.MainServiceEchoBlockingServer

@MiskTest(startService = true)
class GrpcReflectTransitiveServiceTest {
  @MiskTestModule
  val module =
    object : KAbstractModule() {
      override fun configure() {
        install(GrpcReflectTestingModule())
        install(WebActionModule.create<FakeMainServiceAction>())
      }
    }

  @Inject lateinit var clientProvider: Provider<ServerReflectionClient>

  @Test
  fun `transitive services are not listed`() {
    val client = clientProvider.get()

    val call = client.ServerReflectionInfo()
    val (requests, responses) = call.executeBlocking()
    responses.use {
      requests.use {
        requests.write(ServerReflectionRequest(list_services = "*"))

        val response = responses.read()
        val serviceNames = response!!.list_services_response!!.service.map { it.name }
        assertThat(serviceNames).containsExactlyInAnyOrder(
          "grpc.reflection.v1alpha.ServerReflection",
          "transitive.MainService",
        )
      }
    }
  }

  @Singleton
  private class FakeMainServiceAction @Inject constructor() :
    MainServiceEchoBlockingServer, WebAction {
    override fun Echo(request: EchoRequest): EchoResponse = error("unsupported")
  }
}
