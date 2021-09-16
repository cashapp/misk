package misk.grpc.miskserver

import com.squareup.wire.AnyMessage
import com.squareup.wire.GrpcStatus
import misk.exceptions.WebActionException
import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import routeguide.Feature
import routeguide.Point
import routeguide.Rectangle
import routeguide.RouteGuideGetFeatureBlockingServer
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import javax.inject.Inject

class GetFeatureGrpcAction @Inject constructor() : WebAction, RouteGuideGetFeatureBlockingServer {
  @LogRequestResponse(bodySampling = 1.0, errorBodySampling = 1.0)
  override fun GetFeature(request: Point): Feature {
    if (request.latitude == -1) {
      throw WebActionException(request.longitude ?: 500, "unexpected latitude error!")
    }
    val latitude = request.latitude ?: 0
    val longitude = request.longitude ?: 0
    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
      throw WebActionException(
        code = HTTP_BAD_REQUEST,
        responseBody = "invalid coordinates",
        message = "invalid coordinates",
        grpcStatus = GrpcStatus.INVALID_ARGUMENT,
        details = listOf(
          AnyMessage.pack(
            Rectangle(
              lo = Point(-90, -180),
              hi = Point(90, 180)
            )
          ),
          AnyMessage.pack(Point(latitude, longitude))
        )
      )
    }
    return Feature(name = "maple tree", location = request)
  }
}
