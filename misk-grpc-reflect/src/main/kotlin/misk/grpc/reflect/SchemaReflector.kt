package misk.grpc.reflect

import com.squareup.wire.schema.Location
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.SchemaLoader
import grpc.reflection.v1alpha.ServerReflectionRequest
import grpc.reflection.v1alpha.ServerReflectionResponse
import java.nio.file.FileSystems

/**
 * This converts a Wire Schema model to a protobuf DescriptorProtos model and serves that.
 */
// TODO: pass in sourcePath and protoPath (maybe in module?)
class SchemaReflector(sourcePath: List<Location>, protoPath: List<Location>) : ServiceReflector {
  private val schema: Schema = SchemaLoader(FileSystems.getDefault()).run {
    initRoots(
      sourcePath = sourcePath,
      protoPath = protoPath,
    )
    loadSchema()
  }

  override fun process(request: ServerReflectionRequest): ServerReflectionResponse {
    TODO("Use wire SchemaReflector to get data from Schema")
  }

}
