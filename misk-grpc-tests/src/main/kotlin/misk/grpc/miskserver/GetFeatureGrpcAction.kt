package misk.grpc.miskserver

import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import routeguide.Feature
import routeguide.Point
import routeguide.RouteGuideGetFeatureBlockingServer
import javax.inject.Inject

class GetFeatureGrpcAction @Inject constructor() : WebAction, RouteGuideGetFeatureBlockingServer {
  @LogRequestResponse(sampling = 1.0, includeBody = true)
  override fun GetFeature(request: Point): Feature {
    return Feature(name = "maple tree", location = request)
  }
}
