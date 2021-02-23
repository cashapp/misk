package misk.grpc

import grpc.reflection.v1alpha.ServerReflectionClient
import grpc.reflection.v1alpha.ServerReflectionRequest
import javax.inject.Inject
import javax.inject.Provider
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class GrpcReflectTest {
  @MiskTestModule
  val module = GrpcReflectTestingModule()

  @Inject lateinit var clientProvider: Provider<ServerReflectionClient>

  @Test
  fun happyPath() {
    val client = clientProvider.get()

    val call = client.ServerReflectionInfo()
    val (requests, responses) = call.executeBlocking()

    requests.write(ServerReflectionRequest.Builder()
      .list_services("*")
      .build())

    val firstResponse = responses.read()
    println(firstResponse)

    // Waiting for interactive calls.
    Thread.sleep(1000 * 500)
  }
}
