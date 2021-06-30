package misk.grpc.miskserver

import misk.exceptions.WebActionException
import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import routeguide.Feature
import routeguide.Point
import routeguide.RouteGuideGetFeatureBlockingServer
import javax.inject.Inject

class GetFeatureGrpcAction @Inject constructor() : WebAction, RouteGuideGetFeatureBlockingServer {
  @LogRequestResponse(bodySampling = 1.0, errorBodySampling = 1.0)
  override fun GetFeature(request: Point): Feature {
    if (request.latitude == -1) {
      throw WebActionException(request.longitude ?: 500, "unexpected latitude error!")
    }
    return Feature(name = "maple tree", location = request)
  }
}
