package misk.grpc

import io.grpc.stub.StreamObserver
import routeguide.RouteGuideGrpc.RouteGuideImplBase
import routeguide.RouteGuideProto.Feature
import routeguide.RouteGuideProto.Point
import javax.inject.Singleton

@Singleton
class RouteGuideProtocService : RouteGuideImplBase() {
  override fun getFeature(point: Point, responseObserver: StreamObserver<Feature>) {
    responseObserver.onNext(Feature.newBuilder()
        .setName("maple tree")
        .setLocation(point)
        .build())
    responseObserver.onCompleted()
  }
}