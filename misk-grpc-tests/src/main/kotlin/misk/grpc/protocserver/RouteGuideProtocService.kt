package misk.grpc.protocserver

import io.grpc.stub.StreamObserver
import routeguide.RouteGuideGrpc.RouteGuideImplBase
import routeguide.RouteGuideProto
import routeguide.RouteGuideProto.Feature
import routeguide.RouteGuideProto.Point
import javax.inject.Singleton

@Singleton
class RouteGuideProtocService : RouteGuideImplBase() {
  var afterFeatureThrowable: Throwable? = null

  override fun getFeature(point: Point, responseObserver: StreamObserver<Feature>) {
    responseObserver.onNext(Feature.newBuilder()
        .setName("pine tree")
        .setLocation(point)
        .build())

    if (afterFeatureThrowable != null) {
      responseObserver.onError(afterFeatureThrowable)
    } else {
      responseObserver.onCompleted()
    }
  }

  override fun routeChat(responseObserver: StreamObserver<RouteGuideProto.RouteNote>): StreamObserver<RouteGuideProto.RouteNote> {
    println("routeChat")

    responseObserver.onNext(RouteGuideProto.RouteNote.newBuilder()
        .setMessage("helloo from the server")
        .build())

    return object : StreamObserver<RouteGuideProto.RouteNote> {
      override fun onNext(value: RouteGuideProto.RouteNote) {
        println("onNext")
        responseObserver.onNext(RouteGuideProto.RouteNote.newBuilder()
            .setMessage("ACK : " + value.message)
            .build())

        if (afterFeatureThrowable != null) {
          responseObserver.onError(afterFeatureThrowable)
        } else {
          responseObserver.onCompleted()
        }
      }

      override fun onError(t: Throwable?) {
        println("onError")
      }

      override fun onCompleted() {
        println("onCompleted")
      }
    }
  }
}