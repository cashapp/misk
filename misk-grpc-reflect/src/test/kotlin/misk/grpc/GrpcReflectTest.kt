package misk.grpc

import com.squareup.wire.schema.Schema
import grpc.reflection.v1alpha.ListServiceResponse
import grpc.reflection.v1alpha.ServerReflectionClient
import grpc.reflection.v1alpha.ServerReflectionRequest
import grpc.reflection.v1alpha.ServerReflectionResponse
import grpc.reflection.v1alpha.ServiceResponse
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import multipleservices.PaintRequest
import multipleservices.PaintResponse
import multipleservices.RedPaintServicePaintItRedBlockingServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import routeguide.Feature
import routeguide.Point
import routeguide.RouteGuideGetFeatureBlockingServer
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@MiskTest(startService = true)
class GrpcReflectTest {
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(GrpcReflectTestingModule())
      install(WebActionModule.create<FakeRouteGuideGetFeatureBlockingServer>())
      install(WebActionModule.create<FakeRedPaintServicePaintItRedBlockingServer>())
    }
  }

  @Inject lateinit var clientProvider: Provider<ServerReflectionClient>
  @Inject lateinit var schema: Schema
  @Inject lateinit var webActions: List<WebActionEntry>

  /**
   * Note that this test expects three services returned:
   *
   *  * ServerReflection (which is how this feature is implemented)
   *  * RedPaintService (whose `PaintItRed` action is explicitly bound)
   *  * RouteGuide (whose `GetFeature` action is explicitly bound)
   *
   * It also requires that the `BluePaintService` service is not returned. This service is in the
   * same file as `RedPaintService`, but because none of its actions are implemented by this service
   * it is not included in the [ServerReflectionResponse].
   */
  @Test
  fun `happy path`() {
    val client = clientProvider.get()

    val call = client.ServerReflectionInfo()
    val (requests, responses) = call.executeBlocking()
    responses.use {
      requests.use {
        val request = ServerReflectionRequest(list_services = "*")
        requests.write(request)

        val firstResponse = responses.read()
        assertThat(firstResponse).isEqualTo(
          ServerReflectionResponse(
            original_request = request,
            list_services_response = ListServiceResponse(
              service = listOf(
                ServiceResponse(name = "grpc.reflection.v1alpha.ServerReflection"),
                ServiceResponse(name = "multipleservices.RedPaintService"),
                ServiceResponse(name = "routeguide.RouteGuide"),
              )
            )
          )
        )
      }
    }
  }

  /** Just an endpoint so we can have more sample data to reflect upon. */
  @Singleton
  private class FakeRouteGuideGetFeatureBlockingServer @Inject constructor(
  ) : RouteGuideGetFeatureBlockingServer, WebAction {
    override fun GetFeature(request: Point): Feature = error("unsupported")
  }

  /** Another endpoint for sample data. */
  @Singleton
  private class FakeRedPaintServicePaintItRedBlockingServer @Inject constructor(
  ) : RedPaintServicePaintItRedBlockingServer, WebAction {
    override fun PaintItRed(request: PaintRequest): PaintResponse = error("unsupported")
  }
}
