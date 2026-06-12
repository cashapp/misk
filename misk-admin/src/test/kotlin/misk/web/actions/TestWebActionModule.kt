package misk.web.actions

import com.squareup.protos.test.parsing.Shipment
import com.squareup.protos.test.parsing.Warehouse
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import jakarta.inject.Inject
import misk.MiskCaller
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody

// Common module for web action-related tests to use to use that bind up some sample web actions
class TestWebActionModule : KAbstractModule() {
  override fun configure() {
    install(WebServerTestingModule())
    install(MiskTestingServiceModule())
    install(AccessControlModule())

    install(WebActionModule.create<CustomServiceAccessAction>())
    install(WebActionModule.create<CustomCapabilityAccessAction>())
    install(WebActionModule.create<RequestTypeAction>())
    install(WebActionModule.create<GrpcAction>())
    install(WebActionModule.create<DataClassRequestAction>())

    multibind<AccessAnnotationEntry>()
      .toInstance(AccessAnnotationEntry<CustomServiceAccess>(services = listOf("payments")))
    multibind<AccessAnnotationEntry>()
      .toInstance(AccessAnnotationEntry<CustomCapabilityAccess>(capabilities = listOf("admin")))
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
  }

  // TODO(jwilson): get Wire to generate this interface.
  interface ShippingGetDestinationWarehouseBlockingServer : Service {
    @WireRpc(
      path = "/test/GetDestinationWarehouse",
      requestAdapter = "com.squareup.protos.test.parsing.Shipment#ADAPTER",
      responseAdapter = "com.squareup.protos.test.parsing.Warehouse#ADAPTER",
    )
    fun GetDestinationWarehouse(requestType: Shipment): Warehouse
  }
}

class CustomServiceAccessAction @Inject constructor() : WebAction {
  @Inject lateinit var scopedCaller: ActionScoped<MiskCaller?>

  @Get("/custom_service_access")
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  @CustomServiceAccess
  fun get() = "${scopedCaller.get()} authorized as custom service".toResponseBody()
}

@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION) annotation class CustomServiceAccess

class CustomCapabilityAccessAction @Inject constructor() : WebAction {
  @Inject lateinit var scopedCaller: ActionScoped<MiskCaller?>

  @Get("/custom_capability_access")
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  @CustomCapabilityAccess
  fun get() = "${scopedCaller.get()} authorized with custom capability".toResponseBody()
}

@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION) annotation class CustomCapabilityAccess

class RequestTypeAction @Inject constructor() : WebAction {
  @Post("/request_type")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  @Unauthenticated
  fun shipment(@RequestBody requestType: Shipment) = "request: $requestType".toResponseBody()
}

class GrpcAction @Inject constructor() : TestWebActionModule.ShippingGetDestinationWarehouseBlockingServer, WebAction {
  @Unauthenticated
  override fun GetDestinationWarehouse(requestType: Shipment): Warehouse {
    return Warehouse.Builder().warehouse_id(7777L).build()
  }
}

data class DataClassRequest(val token: String, val count: Int, val entries: List<DataClassEntry>)

data class DataClassEntry(val id: Long, val note: String?)

class DataClassRequestAction @Inject constructor() : WebAction {
  @Post("/data_class_request")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  @Unauthenticated
  fun handle(@RequestBody request: DataClassRequest) = "request: $request".toResponseBody()
}
