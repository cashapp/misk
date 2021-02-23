package misk.grpc.reflect

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto
import com.squareup.wire.schema.MessageType
import com.squareup.wire.schema.ProtoFile
import com.squareup.wire.schema.Rpc
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.Service
import grpc.reflection.v1alpha.FileDescriptorResponse
import grpc.reflection.v1alpha.ListServiceResponse
import grpc.reflection.v1alpha.ServerReflectionRequest
import grpc.reflection.v1alpha.ServerReflectionResponse
import grpc.reflection.v1alpha.ServiceResponse
import okio.ByteString.Companion.toByteString

/**
 * This converts a Wire Schema model to a protobuf DescriptorProtos model and serves that.
 */
class SchemaReflector(
  private val schema: Schema
) : ServiceReflector {
  override fun process(request: ServerReflectionRequest): ServerReflectionResponse {
    when {
      request.list_services == "*" -> {
        val service = schema.getService("routeguide.RouteGuide")!!
        val protoFile = schema.protoFile(service.location.path)!!

        val packagePrefix = when {
          protoFile.packageName != null -> protoFile.packageName + "."
          else -> ""
        }

        return ServerReflectionResponse.Builder()
          .list_services_response(
            ListServiceResponse.Builder()
              .service(
                listOf(
                  ServiceResponse.Builder()
                    .name(packagePrefix + service.name)
                    .build()
                )
              )
              .build()
          )
          .build()

      }

      request.file_by_filename != null -> {
        val protoFile = schema.protoFile(request.file_by_filename)!!
        return ServerReflectionResponse.Builder()
          .file_descriptor_response(protoFile.toFileDescriptorResponse())
          .build()

      }

      request.file_containing_symbol != null -> {
        val service = schema.getService(request.file_containing_symbol)!!
        val protoFile = schema.protoFile(service.location.path)!!
        return ServerReflectionResponse.Builder()
          .file_descriptor_response(protoFile.toFileDescriptorResponse())
          .build()

      }

      else -> {
        // TODO.
        println(request)
        return ServerReflectionResponse.Builder()
          .build()
      }
    }
  }
}


private fun ProtoFile.toFileDescriptorResponse(): FileDescriptorResponse {
  val fileDescriptor = toFileDescriptor()
  return FileDescriptorResponse.Builder()
    .file_descriptor_proto(listOf(fileDescriptor.toByteArray().toByteString()))
    .build()
}

private fun ProtoFile.toFileDescriptor(): FileDescriptorProto {
  val result = FileDescriptorProto.newBuilder()
    .setName(this.location.path)
    .setPackage(this.packageName ?: "")

  for (service in services) {
    result.addService(service.toServiceDescriptorProto())
  }

  for (type in types) {
    if (type is MessageType) {
      result.addMessageType(type.toDescriptorProto())
    } else {
      // TODO.
    }
  }

  return result.build()
}

private fun MessageType.toDescriptorProto(): DescriptorProto {
  val result = DescriptorProto.newBuilder()
    .setName(type.simpleName)

  result.addField(
    FieldDescriptorProto.newBuilder()
    .setName("text")
    .setTypeName("string")
    .setType(FieldDescriptorProto.Type.TYPE_STRING)
    .build())

  return result.build()
}

private fun Service.toServiceDescriptorProto(): ServiceDescriptorProto {
  val result = ServiceDescriptorProto.newBuilder()
    .setName(name)

  for (rpc in rpcs) {
    result.addMethod(rpc.toMethodDescriptorProto())
  }

  return result.build()
}

private fun Rpc.toMethodDescriptorProto(): MethodDescriptorProto {
  return MethodDescriptorProto.newBuilder()
    .setName(name)
    .setInputType(requestType.toString())
    .setOutputType(responseType.toString())
    .build()
}
