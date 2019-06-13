package misk.grpc

import com.squareup.wire.ProtoAdapter
import misk.Action
import misk.web.DispatchMechanism
import misk.web.FeatureBinding
import misk.web.FeatureBinding.Claimer
import misk.web.FeatureBinding.Subject
import misk.web.PathPattern
import okio.BufferedSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.jvm.javaType

internal class GrpcRequestFeatureBinding(
  private val adapter: ProtoAdapter<Any>,
  private val streaming: Boolean
) : FeatureBinding {
  override fun bind(subject: Subject) {
    val requestBody = subject.takeRequestBody()
    val receiveChannel = receiveChannel(requestBody)

    if (streaming) {
      subject.setParameter(0, receiveChannel)
    } else {
      val request = receiveChannel.receiveOrNull()!!
      subject.setParameter(0, request)
    }
  }

  private fun receiveChannel(source: BufferedSource): GrpcReceiveChannel<Any> {
    val reader = GrpcReader.get(source, adapter)
    return object : GrpcReceiveChannel<Any> {
      override fun receiveOrNull(): Any? {
        return reader.readMessage()
      }

      override fun close() {
        reader.close()
      }

      override fun toString() = "GrpcReceiveChannel"
    }
  }

  @Singleton
  class Factory @Inject internal constructor() : FeatureBinding.Factory {
    override fun create(
      action: Action,
      pathPattern: PathPattern,
      claimer: Claimer
    ): FeatureBinding? {
      if (action.dispatchMechanism != DispatchMechanism.GRPC) return null

      require(action.parameters.size in 1..2) {
        "@Grpc functions must have either 1 or 2 parameters: $action"
      }

      claimer.claimParameter(0)
      claimer.claimRequestBody()
      val streamingRequestType = action.parameters[0].type.streamElementType()

      return if (streamingRequestType != null) {
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        GrpcRequestFeatureBinding(
            adapter = ProtoAdapter.get(streamingRequestType as Class<Any>),
            streaming = true
        )
      } else {
        @Suppress("UNCHECKED_CAST") // Assume it's a proto type.
        GrpcRequestFeatureBinding(
            adapter = ProtoAdapter.get(action.parameters[0].type.javaType as Class<Any>),
            streaming = false
        )
      }
    }
  }
}