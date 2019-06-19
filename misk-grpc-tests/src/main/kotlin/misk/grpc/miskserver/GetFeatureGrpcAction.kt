package misk.grpc.miskserver

import misk.web.Grpc
import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import routeguide.Feature
import routeguide.Point
import javax.inject.Inject

class GetFeatureGrpcAction @Inject constructor() : WebAction {
  @Grpc("/routeguide.RouteGuide/GetFeature")
  @LogRequestResponse(sampling = 1.0, includeBody = true)
  fun sayHello(point: Point): Feature {
    return Feature(name = "maple tree", location = point)
  }
}