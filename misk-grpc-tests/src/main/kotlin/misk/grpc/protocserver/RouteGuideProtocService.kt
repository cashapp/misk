package misk.grpc.protocserver

import io.grpc.stub.StreamObserver
import routeguide.RouteGuideGrpc.RouteGuideImplBase
import routeguide.RouteGuideProto
import routeguide.RouteGuideProto.Feature
import routeguide.RouteGuideProto.Point
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RouteGuideProtocService @Inject constructor() : RouteGuideImplBase() {
  override fun getFeature(point: Point, responseObserver: StreamObserver<Feature>) {
    responseObserver.onNext(Feature.newBuilder()
        .setName("pine tree")
        .setLocation(point)
        .build())
    responseObserver.onCompleted()
  }

  override fun routeChat(
    responseObserver: StreamObserver<RouteGuideProto.RouteNote>
  ):
      StreamObserver<RouteGuideProto.RouteNote> {
    return object : StreamObserver<RouteGuideProto.RouteNote> {
      override fun onNext(value: RouteGuideProto.RouteNote) {
        responseObserver.onNext(RouteGuideProto.RouteNote.newBuilder()
            .setMessage(value.message.reversed())
            .build())
      }

      override fun onError(t: Throwable?) {
        responseObserver.onCompleted()
      }

      override fun onCompleted() {
        responseObserver.onCompleted()
      }
    }
  }
}
