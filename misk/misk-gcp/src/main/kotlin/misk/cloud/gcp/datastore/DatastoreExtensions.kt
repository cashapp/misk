// Extensions to Google Cloud Datastore types
package misk.cloud.gcp.datastore

import com.google.cloud.datastore.Blob
import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.Key
import com.google.cloud.datastore.QueryResults
import com.squareup.wire.ProtoAdapter
import okio.ByteString
import okio.ByteString.Companion.toByteString

fun Blob.toByteString(): ByteString = asReadOnlyByteBuffer().toByteString()

fun Entity.getByteString(name: String) = getBlob(name).toByteString()

fun <T> Entity.getProto(name: String, protoAdapter: ProtoAdapter<T>): T =
    protoAdapter.decode(getByteString(name))

fun Entity.Builder.set(name: String, bytes: ByteString): Entity.Builder =
    set(name, Blob.copyFrom(bytes.asByteBuffer()))

fun <T> QueryResults<T>.asList(): List<T> = asSequence().toList()

object Keys {
  fun newKey(projectId: String, kind: String, id: Long): Key =
      Key.newBuilder(projectId, kind, id).build()

  fun newKey(projectId: String, kind: String, name: String): Key =
      Key.newBuilder(projectId, kind, name).build()
}
