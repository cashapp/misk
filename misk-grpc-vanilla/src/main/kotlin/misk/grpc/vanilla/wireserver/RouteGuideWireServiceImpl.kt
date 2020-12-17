package misk.grpc.vanilla.wireserver

import io.grpc.stub.StreamObserver
import routeguide.Feature
import routeguide.Point
import routeguide.RouteNote
import javax.inject.Inject
import javax.inject.Singleton

// This is the implementation of the grpc service
@Singleton
internal class RouteGuideWireServiceImpl @Inject constructor() : RouteGuideWireGrpc.RouteGuideImplBase() {
  override fun getFeature(request: Point, response: StreamObserver<Feature>) {
    response.onNext(Feature(name = "pine tree", location = request))
    response.onCompleted()
  }

  override fun routeChat(response: StreamObserver<RouteNote>):
    StreamObserver<RouteNote> {
    return object : StreamObserver<RouteNote> {
      override fun onNext(value: RouteNote) {
        response.onNext(RouteNote(message = value.message?.reversed()))
      }

      override fun onError(t: Throwable?) {
        response.onCompleted()
      }

      override fun onCompleted() {
        response.onCompleted()
      }
    }
  }
}
