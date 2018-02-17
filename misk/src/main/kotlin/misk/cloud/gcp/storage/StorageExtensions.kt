package misk.cloud.gcp.storage

import com.google.api.gax.paging.Page
import com.google.cloud.ReadChannel
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import java.nio.ByteBuffer

/** @return compares one blob id to another; orders by bucket, then name, then generation */
fun BlobId.compareTo(other: BlobId): Int {
  val bucketCompare = bucket.compareTo(other.bucket)
  if (bucketCompare != 0) return bucketCompare

  val nameCompare = name.compareTo(other.name)
  if (nameCompare != 0) return nameCompare

  return ((generation ?: 0) - (other.generation ?: 0)).toInt()
}

/** @return a list containing all of the elements in this page */
fun <T> Page<T>.toList(): List<T> = values.asSequence().toList()

/** @return a list containing just the ids of the blobs in the page */
val Page<Blob>.blobIds get() = toList().map { it.blobId }

/** Runs the given block for each chunk on a given channel */
fun ReadChannel.forEachChunk(
    buffer: ByteBuffer,
    action: (ByteBuffer, Int) -> Unit
) {
  setChunkSize(buffer.capacity())
  buffer.clear()

  var bytesRead = read(buffer)
  while (bytesRead != -1) {
    buffer.position(0)
    action(buffer, bytesRead)
    buffer.clear()
    bytesRead = read(buffer)
  }
}

fun ReadChannel.forEachChunk(
    chunkSize: Int,
    action: (ByteBuffer, Int) -> Unit
) {
  forEachChunk(ByteBuffer.allocate(chunkSize), action)
}
