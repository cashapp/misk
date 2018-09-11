package misk.grpc.miskserver

import misk.web.Grpc
import misk.web.RequestBody
import misk.web.actions.WebAction
import routeguide.Feature
import routeguide.Point

class GetFeatureGrpcAction : WebAction {
  @Grpc("/routeguide.RouteGuide/GetFeature")
  fun sayHello(@RequestBody point: Point): Feature {
    return Feature.Builder()
        .name("maple tree")
        .location(point)
        .build()
  }
}