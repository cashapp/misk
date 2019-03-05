package misk.grpc.protocserver

import io.grpc.stub.StreamObserver
import routeguide.RouteGuideGrpc.RouteGuideImplBase
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
}