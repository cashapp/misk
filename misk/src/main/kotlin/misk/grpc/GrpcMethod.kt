package misk.grpc

import com.squareup.wire.ProtoAdapter

// TODO(jwilson): Replace this awkward beast with a Retrofit2 interface.
class GrpcMethod<S, R>(
  val path: String,
  val requestAdapter: ProtoAdapter<S>,
  val responseAdapter: ProtoAdapter<R>
)
