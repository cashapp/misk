package misk.grpc.vanilla.wireserver

import io.grpc.BindableService
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.MethodDescriptor
import io.grpc.MethodDescriptor.generateFullMethodName
import io.grpc.ServerServiceDefinition
import io.grpc.ServiceDescriptor
import io.grpc.stub.AbstractStub
import io.grpc.stub.ClientCalls
import io.grpc.stub.ClientCalls.blockingServerStreamingCall
import io.grpc.stub.ClientCalls.blockingUnaryCall
import io.grpc.stub.ServerCalls.asyncBidiStreamingCall
import io.grpc.stub.ServerCalls.asyncClientStreamingCall
import io.grpc.stub.ServerCalls.asyncServerStreamingCall
import io.grpc.stub.ServerCalls.asyncUnaryCall
import io.grpc.stub.StreamObserver
import misk.grpc.consumeEachAndClose
import misk.grpc.vanilla.wireserver.adapters.MessageSinkAdapter
import misk.grpc.vanilla.wireserver.adapters.MessageSourceAdapter
import routeguide.Feature
import routeguide.Point
import routeguide.Rectangle
import routeguide.RouteGuideGetFeatureBlockingServer
import routeguide.RouteGuideListFeaturesBlockingServer
import routeguide.RouteGuideRecordRouteBlockingServer
import routeguide.RouteGuideRouteChatBlockingServer
import routeguide.RouteNote
import routeguide.RouteSummary
import java.io.InputStream
import java.util.concurrent.ExecutorService

// TODO: Wire compiler will generate this
object RouteGuideWireGrpc {
  val SERVICE_NAME = "routeguide.RouteGuide"

  @Volatile
  private var serviceDescriptor: ServiceDescriptor? = null

  fun getServiceDescriptor(): ServiceDescriptor? {
    var result = serviceDescriptor
    if (result == null) {
      synchronized(RouteGuideWireGrpc::class) {
        result = serviceDescriptor
        if (result == null) {
          result = ServiceDescriptor.newBuilder(SERVICE_NAME)
            .addMethod(getGetFeatureMethod())
            .addMethod(getListFeaturesMethod())
            .addMethod(getRecordRouteMethod())
            .addMethod(getRouteChatMethod())
            .build()
          serviceDescriptor = result
        }
      }
    }
    return result
  }

  @Volatile
  private var getGetFeatureMethod: MethodDescriptor<Point, Feature>? = null

  fun getGetFeatureMethod(): MethodDescriptor<Point, Feature> {
    var result: MethodDescriptor<Point, Feature>? = getGetFeatureMethod
    if (result == null) {
      synchronized(RouteGuideWireGrpc::class) {
        result = getGetFeatureMethod
        if (result == null) {
          getGetFeatureMethod = MethodDescriptor.newBuilder<Point, Feature>()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(
              generateFullMethodName(
                "routeguide.RouteGuide", "GetFeature"
              )
            )
            .setSampledToLocalTracing(true)
            .setRequestMarshaller(RouteGuideImplBase.PointMarshaller())
            .setResponseMarshaller(RouteGuideImplBase.FeatureMarshaller())
            .build()
        }
      }
    }
    return getGetFeatureMethod!!
  }

  @Volatile
  private var getListFeaturesMethod: MethodDescriptor<Rectangle, Feature>? = null

  fun getListFeaturesMethod(): MethodDescriptor<Rectangle, Feature> {
    var result: MethodDescriptor<Rectangle, Feature>? = getListFeaturesMethod
    if (result == null) {
      synchronized(RouteGuideWireGrpc::class) {
        result = getListFeaturesMethod
        if (result == null) {
          getListFeaturesMethod = MethodDescriptor.newBuilder<Rectangle, Feature>()
            .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
            .setFullMethodName(
              generateFullMethodName(
                "routeguide.RouteGuide", "ListFeatures"
              )
            )
            .setSampledToLocalTracing(true)
            .setRequestMarshaller(RouteGuideImplBase.RectangleMarshaller())
            .setResponseMarshaller(RouteGuideImplBase.FeatureMarshaller())
            .build()
        }
      }
    }
    return getListFeaturesMethod!!
  }

  @Volatile
  private var getRecordRouteMethod: MethodDescriptor<Point, RouteSummary>? = null

  fun getRecordRouteMethod(): MethodDescriptor<Point, RouteSummary> {
    var result: MethodDescriptor<Point, RouteSummary>? = getRecordRouteMethod
    if (result == null) {
      synchronized(RouteGuideWireGrpc::class) {
        result = getRecordRouteMethod
        if (result == null) {
          getRecordRouteMethod = MethodDescriptor.newBuilder<Point, RouteSummary>()
            .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
            .setFullMethodName(
              generateFullMethodName(
                "routeguide.RouteGuide", "RecordRoute"
              )
            )
            .setSampledToLocalTracing(true)
            .setRequestMarshaller(RouteGuideImplBase.PointMarshaller())
            .setResponseMarshaller(RouteGuideImplBase.RouteSummaryMarshaller())
            .build()
        }
      }
    }
    return getRecordRouteMethod!!
  }

  @Volatile
  private var getRouteChatMethod: MethodDescriptor<RouteNote, RouteNote>? = null

  fun getRouteChatMethod(): MethodDescriptor<RouteNote, RouteNote> {
    var result: MethodDescriptor<RouteNote, RouteNote>? = getRouteChatMethod
    if (result == null) {
      synchronized(RouteGuideWireGrpc::class) {
        result = getRouteChatMethod
        if (result == null) {
          getRouteChatMethod = MethodDescriptor.newBuilder<RouteNote, RouteNote>()
            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName(
              generateFullMethodName(
                "routeguide.RouteGuide", "RouteChat"
              )
            )
            .setSampledToLocalTracing(true)
            .setRequestMarshaller(RouteGuideImplBase.RouteNoteMarshaller())
            .setResponseMarshaller(RouteGuideImplBase.RouteNoteMarshaller())
            .build()
        }
      }
    }
    return getRouteChatMethod!!
  }

  fun newStub(channel: Channel): RouteGuideStub = RouteGuideStub(channel)
  fun newBlockingStub(channel: Channel): RouteGuideBlockingStub = RouteGuideBlockingStub(channel)

  abstract class RouteGuideImplBase : BindableService {
     open fun getFeature(request: Point, response: StreamObserver<Feature>) {
      TODO("not implemented")
    }

    open fun listFeatures(request: Rectangle, response: StreamObserver<Feature>) {
      TODO("not implemented")
    }

    open fun recordRoute(response: StreamObserver<RouteSummary>): StreamObserver<Point> {
      TODO("not implemented")
    }

    open fun routeChat(response: StreamObserver<RouteNote>): StreamObserver<RouteNote> {
      TODO("not implemented")
    }

    override fun bindService(): ServerServiceDefinition {
      return ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getGetFeatureMethod(),
          asyncUnaryCall(this@RouteGuideImplBase::getFeature)
        )
        .addMethod(
          getListFeaturesMethod(),
          asyncServerStreamingCall(this@RouteGuideImplBase::listFeatures)
        )
        .addMethod(
          getRecordRouteMethod(),
          asyncClientStreamingCall(this@RouteGuideImplBase::recordRoute)
        )
        .addMethod(
          getRouteChatMethod(),
          asyncBidiStreamingCall(this@RouteGuideImplBase::routeChat)
        )
        .build()
    }

    class RectangleMarshaller : MethodDescriptor.Marshaller<Rectangle> {
      override fun stream(value: Rectangle): InputStream = Rectangle.ADAPTER.encode(value)
        .inputStream()

      override fun parse(stream: InputStream): Rectangle = Rectangle.ADAPTER.decode(stream)
    }

    class PointMarshaller : MethodDescriptor.Marshaller<Point> {
      override fun stream(value: Point): InputStream = Point.ADAPTER.encode(value).inputStream()
      override fun parse(stream: InputStream): Point = Point.ADAPTER.decode(stream)
    }

    class FeatureMarshaller : MethodDescriptor.Marshaller<Feature> {
      override fun stream(value: Feature): InputStream = Feature.ADAPTER.encode(value).inputStream()
      override fun parse(stream: InputStream): Feature = Feature.ADAPTER.decode(stream)
    }

    class RouteSummaryMarshaller : MethodDescriptor.Marshaller<RouteSummary> {
      override fun stream(value: RouteSummary): InputStream = RouteSummary.ADAPTER.encode(value)
        .inputStream()

      override fun parse(stream: InputStream): RouteSummary = RouteSummary.ADAPTER.decode(stream)
    }

    class RouteNoteMarshaller : MethodDescriptor.Marshaller<RouteNote> {
      override fun stream(value: RouteNote): InputStream = RouteNote.ADAPTER.encode(value)
        .inputStream()

      override fun parse(stream: InputStream): RouteNote = RouteNote.ADAPTER.decode(stream)
    }
  }

  // Adapter for integrating code written against Wire's gRPC server with the official Google
  // gRPC server.
  // TODO: This should inject Providers to not instantiate all dependencies for all methods.
  class RouteGuideImplLegacyAdapter constructor(
    private val getFeature: RouteGuideGetFeatureBlockingServer?,
    private val listFeatures: RouteGuideListFeaturesBlockingServer?,
    private val recordRoute: RouteGuideRecordRouteBlockingServer?,
    private val routeChat: RouteGuideRouteChatBlockingServer?,
    private val streamExecutor: ExecutorService
  ): RouteGuideImplBase() {
    override fun getFeature(request: Point, response: StreamObserver<Feature>) {
      response.onNext(getFeature!!.GetFeature(request))
      response.onCompleted()
    }

    override fun listFeatures(request: Rectangle, response: StreamObserver<Feature>) {
      listFeatures!!.ListFeatures(request, MessageSinkAdapter(response))
    }

    override fun recordRoute(response: StreamObserver<RouteSummary>): StreamObserver<Point> {
      val requestStream = MessageSourceAdapter<Point>()
      streamExecutor.submit {
        response.onNext(recordRoute!!.RecordRoute(requestStream))
        response.onCompleted()
      }
      return requestStream
    }

    override fun routeChat(response: StreamObserver<RouteNote>): StreamObserver<RouteNote> {
      val requestStream = MessageSourceAdapter<RouteNote>()
      streamExecutor.submit {
        routeChat!!.RouteChat(requestStream, MessageSinkAdapter(response))
      }
      return requestStream
    }
  }

  class RouteGuideStub : AbstractStub<RouteGuideStub> {
    internal constructor(channel: Channel) : super(channel)
    internal constructor(channel: Channel, callOptions: CallOptions) : super(channel, callOptions)

    override fun build(channel: Channel, callOptions: CallOptions): RouteGuideStub {
      return RouteGuideStub(channel, callOptions)
    }

    fun getFeature(request: Point, response: StreamObserver<Feature>) {
      ClientCalls.asyncUnaryCall(
        channel.newCall(getGetFeatureMethod(), callOptions), request, response
      )
    }

    fun listFeatures(request: Rectangle, response: StreamObserver<Feature>) {
      ClientCalls.asyncServerStreamingCall(
        channel.newCall(getListFeaturesMethod(), callOptions), request, response
      )
    }

    fun recordRoute(response: StreamObserver<RouteSummary>): StreamObserver<Point> {
      return ClientCalls.asyncClientStreamingCall(
        channel.newCall(getRecordRouteMethod(), callOptions), response
      )
    }

    fun routeChat(response: StreamObserver<RouteNote>): StreamObserver<RouteNote> {
      return ClientCalls.asyncBidiStreamingCall(
        channel.newCall(getRouteChatMethod(), callOptions), response
      )
    }
  }

  class RouteGuideBlockingStub : AbstractStub<RouteGuideBlockingStub> {
    internal constructor(channel: Channel) : super(channel)
    internal constructor(channel: Channel, callOptions: CallOptions) : super(channel, callOptions)

    override fun build(channel: Channel, callOptions: CallOptions): RouteGuideBlockingStub {
      return RouteGuideBlockingStub(channel, callOptions)
    }

    fun getFeature(request: Point): Feature {
      return blockingUnaryCall(channel, getGetFeatureMethod(), callOptions, request)
    }

    fun listFeatures(request: Rectangle): Iterator<Feature> {
      return blockingServerStreamingCall(channel, getListFeaturesMethod(), callOptions, request)
    }
  }
}
