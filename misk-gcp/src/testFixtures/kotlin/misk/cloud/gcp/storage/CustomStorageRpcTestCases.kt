package misk.cloud.gcp.storage

import com.google.cloud.NoCredentials
import com.google.cloud.storage.Blob.BlobSourceOption
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.BlobListOption.currentDirectory
import com.google.cloud.storage.Storage.BlobListOption.prefix
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import com.google.cloud.storage.spi.v1.StorageRpc
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.test.assertFailsWith

/** Test cases covering custom storage RPC implementations, used for testing local and in-memory storage */
internal abstract class CustomStorageRpcTestCases<T : StorageRpc> {
  lateinit var rpc: T
  lateinit var storage: Storage

  @BeforeEach
  fun buildStorage() {
    rpc = newStorageRpc()
    storage = StorageOptions.newBuilder()
      .setCredentials(NoCredentials.getInstance())
      .setServiceRpcFactory { _ -> rpc }
      .build()
      .service
  }

  @Test
  fun createEmpty() {
    val blobId = BlobId.of("my_bucket", "my_entry")
    val result = storage.create(
      BlobInfo.newBuilder(blobId)
        .setContentType("text/plain")
        .build()
    )

    assertThat(result.blobId).isEqualTo(BlobId.of(blobId.bucket, blobId.name, 1))
    assertThat(result.generation).isEqualTo(1)
    assertThat(result.metageneration).isEqualTo(1)
    assertThat(result.contentType).isEqualTo("text/plain")
    assertThat(result.getContent()).isEmpty()
  }

  @Test
  fun createWithContent() {
    val contents = "This is my text"
    val blobId = BlobId.of("my_bucket", "my_entry")
    val result = storage.create(
      BlobInfo.newBuilder(blobId).setContentType("text/plain").build(),
      contents.toByteArray()
    )

    assertThat(result.blobId).isEqualTo(BlobId.of(blobId.bucket, blobId.name, 1))
    assertThat(result.generation).isEqualTo(1)
    assertThat(result.metageneration).isEqualTo(1)
    assertThat(result.contentType).isEqualTo("text/plain")
    assertThat(String(result.getContent())).isEqualTo("This is my text")
  }

  @Test
  fun createIfNotExists() {
    val contents = "This is my text"
    val blobId = BlobId.of("my_bucket", "my_entry")
    val result = storage.create(
      BlobInfo.newBuilder(blobId).setContentType("text/plain").build(),
      contents.toByteArray(),
      Storage.BlobTargetOption.doesNotExist()
    )

    assertThat(result.blobId).isEqualTo(BlobId.of(blobId.bucket, blobId.name, 1))
    assertThat(result.generation).isEqualTo(1)
    assertThat(result.metageneration).isEqualTo(1)
    assertThat(result.contentType).isEqualTo("text/plain")
    assertThat(String(result.getContent())).isEqualTo("This is my text")

    assertFailsWith<StorageException> {
      val contents2 = "This is my text"
      storage.create(
        BlobInfo.newBuilder(blobId).setContentType("text/plain").build(),
        contents2.toByteArray(),
        Storage.BlobTargetOption.doesNotExist()
      )
    }
  }

  @Test
  fun createIfGenerationMatches() {
    val contents = "This is my text"
    val blobId = BlobId.of("my_bucket", "my_entry")
    val result = storage.create(
      BlobInfo.newBuilder(blobId).setContentType("text/plain").build(),
      contents.toByteArray()
    )

    assertThat(result.blobId).isEqualTo(BlobId.of(blobId.bucket, blobId.name, 1))
    assertThat(result.generation).isEqualTo(1)
    assertThat(result.metageneration).isEqualTo(1)
    assertThat(result.contentType).isEqualTo("text/plain")
    assertThat(String(result.getContent())).isEqualTo("This is my text")

    val contents2 = "This is another text"
    val blobId2 = BlobId.of(blobId.bucket, blobId.name, result.generation)
    val result2 = storage.create(
      BlobInfo.newBuilder(blobId2).setContentType("text/plain").build(),
      contents2.toByteArray(),
      Storage.BlobTargetOption.generationMatch()
    )

    assertThat(result2.blobId).isEqualTo(BlobId.of(blobId.bucket, blobId.name, 2))
    assertThat(result2.generation).isEqualTo(2)
    assertThat(result2.metageneration).isEqualTo(1)
    assertThat(result2.contentType).isEqualTo("text/plain")
    assertThat(String(result.getContent())).isEqualTo("This is another text")

    assertFailsWith<StorageException> {
      val contents3 = "This is the third text"
      storage.create(
        BlobInfo.newBuilder(blobId2).setContentType("text/plain").build(), // Old generation
        contents3.toByteArray(),
        Storage.BlobTargetOption.generationMatch()
      )
    }
  }

  @Test
  fun createUpdatesMetagenerationIfMetadataChanged() {
    val contents = "This is my text"
    val blobId = BlobId.of("my_bucket", "my_entry")
    val result = storage.create(
      BlobInfo.newBuilder(blobId)
        .setContentType("text/plain")
        .setMetadata(mapOf("foo" to "bar"))
        .build(),
      contents.toByteArray()
    )

    assertThat(result.generation).isEqualTo(1)
    assertThat(result.metageneration).isEqualTo(1)
    assertThat(result.contentType).isEqualTo("text/plain")
    assertThat(result.metadata).isEqualTo(mapOf("foo" to "bar"))

    val contents2 = "This is another text"
    val result2 = storage.create(
      BlobInfo.newBuilder(blobId)
        .setContentType("text/plain")
        .setMetadata(mapOf("foo" to "zed"))
        .build(),
      contents2.toByteArray()
    )

    assertThat(result2.generation).isEqualTo(2)
    assertThat(result2.metageneration).isEqualTo(2)
    assertThat(result2.contentType).isEqualTo("text/plain")
    assertThat(result2.metadata).isEqualTo(mapOf("foo" to "zed"))
  }

  @Test
  fun progressiveUpload() {
    val blobId = BlobId.of("my_bucket", "my_entry")
    val blob = storage.create(
      BlobInfo.newBuilder(blobId)
        .setContentType("text/plain")
        .build()
    )

    val upload = "This is my text".repeat(1024 * 256)
    blob.writer().use {
      // Use the minimum chunk size, which is smaller than the buffer size, to
      // ensure that we are sending multiple chunks
      it.setChunkSize(256 * 1024)
      it.write(ByteBuffer.wrap(upload.toByteArray()))
    }

    val download = blob.getContent()
    assertThat(String(download)).isEqualTo(upload)
  }

  @Test
  fun get() {
    val contents = "This is my text"
    val blobId = BlobId.of("my_bucket", "my_entry")
    storage.create(
      BlobInfo.newBuilder(blobId)
        .setContentType("text/plain")
        .build(),
      contents.toByteArray()
    )

    val blob = storage.get(blobId)
    assertThat(blob).isNotNull()
    assertThat(blob.generation).isEqualTo(1)
    assertThat(blob.metageneration).isEqualTo(1)
    assertThat(blob.size).isEqualTo("This is my text".length.toLong())
    assertThat(blob.exists()).isTrue()
    assertThat(String(blob.getContent())).isEqualTo("This is my text")
  }

  @Test
  fun getIfGenerationMatches() {
    // Create the blob
    val contents1 = "This is my text"
    val blobId = BlobId.of("my_bucket", "my_entry")
    storage.create(
      BlobInfo.newBuilder(blobId)
        .setContentType("text/plain")
        .build(),
      contents1.toByteArray()
    )

    val blob1 = storage.get(blobId)

    // Update the blob
    val contents2 = "This is another text"
    storage.create(
      BlobInfo.newBuilder(BlobId.of(blobId.bucket, blobId.name, 1))
        .setContentType("text/plain")
        .build(),
      contents2.toByteArray()
    )

    // The prior returned blob should reflect the old state, and should not be accessible
    // if generation matching is requested (since it has the old generation)
    assertThat(blob1).isNotNull()
    assertThat(blob1.generation).isEqualTo(1)
    assertThat(blob1.metageneration).isEqualTo(1)
    assertThat(blob1.size).isEqualTo(contents1.length.toLong())
    assertThat(blob1.exists()).isTrue()
    assertThat(String(blob1.getContent())).isEqualTo(contents2)
    assertFailsWith<StorageException> {
      blob1.getContent(BlobSourceOption.generationMatch())
    }

    // The newly returned blob should reflect the new state, and should be accessible even
    // if generation match is requested (since it has the current generation)
    val blob2 = storage.get(blobId)
    assertThat(blob2).isNotNull()
    assertThat(blob2.generation).isEqualTo(2)
    assertThat(blob2.metageneration).isEqualTo(1)
    assertThat(blob2.size).isEqualTo(contents2.length.toLong())
    assertThat(blob2.exists()).isTrue()
    assertThat(String(blob2.getContent(BlobSourceOption.generationMatch())))
      .isEqualTo(contents2)
  }

  @Test
  fun getWhenNotExists() {
    assertThat(storage.get(BlobId.of("my_bucket", "my_entry"))).isNull()
  }

  @Test
  fun delete() {
    val contents = "This is my text"
    val blobId = BlobId.of("my_bucket", "my_entry")
    val result = storage.create(
      BlobInfo.newBuilder(blobId)
        .setContentType("text/plain")
        .build(),
      contents.toByteArray()
    )

    val deleted = result.delete()

    assertThat(deleted).isTrue()
    assertThat(storage[result.blobId]).isNull()
  }

  @Test
  fun deleteIfGenerationMatch() {
    // Create the blob
    val contents1 = "This is my text"
    val blobId = BlobId.of("my_bucket", "my_entry")
    val blob1 = storage.create(
      BlobInfo.newBuilder(blobId)
        .setContentType("text/plain")
        .build(),
      contents1.toByteArray()
    )

    // Update the blob
    val contents2 = "This is another text"
    val blob2 = storage.create(
      BlobInfo.newBuilder(blob1.blobId)
        .setContentType("text/plain")
        .build(),
      contents2.toByteArray()
    )

    // Try to delete at the old generation - this should fail
    val deleteOldVersion = blob1.delete(BlobSourceOption.generationMatch())
    assertThat(deleteOldVersion).isFalse()

    // Delete at the new generation - this should succeed
    val deleteNewVersion = blob2.delete(BlobSourceOption.generationMatch())
    assertThat(deleteNewVersion).isTrue()

    assertThat(storage[blob2.blobId]).isNull()
  }

  @Test
  fun deleteWhenNotExists() {
    val deleted = storage.delete(BlobId.of("my_bucket", "my_entry"))
    assertThat(deleted).isFalse()
  }

  @Test
  fun progressiveDownload() {
    val upload = "This is my text".repeat(1024 * 256)
    val blobId = BlobId.of("my_bucket", "my_entry")
    val blob = storage.create(
      BlobInfo.newBuilder(blobId)
        .setContentType("text/plain")
        .build(),
      upload.toByteArray()
    )

    val download = ByteBuffer.allocate(upload.length)

    // Read in smaller chunks
    blob.reader().use {
      // Use the minimum chunk size, which is smaller than the buffer size, to
      // ensure that we are retrieving multiple chunks
      it.forEachChunk(256 * 1024) { buffer, _ -> download.put(buffer) }
    }

    download.position(0)
    assertThat(String(download.array())).isEqualTo(upload)
  }

  @Test
  fun copy() {
    val data = "This is my text".repeat(1024 * 256)
    val sourceBlobId = BlobId.of("my_bucket", "my_entry")
    val sourceBlob = storage.create(
      BlobInfo.newBuilder(sourceBlobId)
        .setContentType("text/plain")
        .build(),
      data.toByteArray()
    )

    val targetBlobId = BlobId.of("my_bucket", "new_entry")

    sourceBlob.copyTo(targetBlobId)

    val targetBlob = storage.get(targetBlobId)
    assertThat(String(targetBlob.getContent())).isEqualTo(data)
  }

  @Test
  fun listFolders() {
    storage.create(
      BlobInfo.newBuilder("my_bucket", "notes/storage/merp.txt")
        .setContentType("text/plain")
        .build(),
      "This is a merp".toByteArray()
    )
    storage.create(
      BlobInfo.newBuilder("my_bucket", "notes/storage/traif.txt")
        .setContentType("text/plain")
        .build(),
      "This is traif".toByteArray()
    )
    storage.create(
      BlobInfo.newBuilder("my_bucket", "notes/misc/blah.txt")
        .setContentType("text/plain")
        .build(),
      "This is a blah".toByteArray()
    )
    storage.create(
      BlobInfo.newBuilder("my_bucket", "notes/misc/boop.txt")
        .setContentType("text/plain")
        .build(),
      "This is a boop".toByteArray()
    )
    storage.create(
      BlobInfo.newBuilder("my_bucket", "notes/top.txt")
        .setContentType("text/plain")
        .build(),
      "This is at the top level of notes".toByteArray()
    )
    storage.create(
      BlobInfo.newBuilder("my_bucket", "notes/top2.txt")
        .setContentType("text/plain")
        .build(),
      "This is also at the top level of notes".toByteArray()
    )
    storage.create(
      BlobInfo.newBuilder("my_bucket", "images/storage.txt")
        .setContentType("text/plain")
        .build(),
      "This is about storage".toByteArray()
    )
    storage.create(
      BlobInfo.newBuilder("my_bucket", "images.txt")
        .setContentType("text/plain")
        .build(),
      "This documents images".toByteArray()
    )
    storage.create(
      BlobInfo.newBuilder("other_bucket", "protos.txt")
        .setContentType("text/plain")
        .build(),
      "These are protos in another bucket".toByteArray()
    )

    val all = storage.list("my_bucket")
    assertThat(all.blobIds).containsExactlyInAnyOrder(
      BlobId.of("my_bucket", "notes/storage/merp.txt", 1),
      BlobId.of("my_bucket", "notes/storage/traif.txt", 1),
      BlobId.of("my_bucket", "notes/misc/blah.txt", 1),
      BlobId.of("my_bucket", "notes/misc/boop.txt", 1),
      BlobId.of("my_bucket", "notes/top.txt", 1),
      BlobId.of("my_bucket", "notes/top2.txt", 1),
      BlobId.of("my_bucket", "images/storage.txt", 1),
      BlobId.of("my_bucket", "images.txt", 1)
    )

    val roots = storage.list("my_bucket", currentDirectory())
    assertThat(roots.blobIds).containsExactlyInAnyOrder(
      BlobId.of("my_bucket", "notes"),
      BlobId.of("my_bucket", "images"),
      BlobId.of("my_bucket", "images.txt", 1)
    )

    val subDir = storage.list("my_bucket", prefix("notes/"), currentDirectory())
    assertThat(subDir.blobIds).containsExactlyInAnyOrder(
      BlobId.of("my_bucket", "notes/misc"),
      BlobId.of("my_bucket", "notes/storage"),
      BlobId.of("my_bucket", "notes/top.txt", 1),
      BlobId.of("my_bucket", "notes/top2.txt", 1)
    )

    val subDirRecursive = storage.list("my_bucket", prefix("notes/"))
    assertThat(subDirRecursive.blobIds).containsExactlyInAnyOrder(
      BlobId.of("my_bucket", "notes/storage/merp.txt", 1),
      BlobId.of("my_bucket", "notes/storage/traif.txt", 1),
      BlobId.of("my_bucket", "notes/misc/blah.txt", 1),
      BlobId.of("my_bucket", "notes/misc/boop.txt", 1),
      BlobId.of("my_bucket", "notes/top.txt", 1),
      BlobId.of("my_bucket", "notes/top2.txt", 1)
    )

    val partialNameMatch =
      storage.list("my_bucket", prefix("notes/s"), currentDirectory())
    assertThat(partialNameMatch.blobIds).containsExactlyInAnyOrder(
      BlobId.of("my_bucket", "notes/storage")
    )

    val recursivePartialNameMatch = storage.list("my_bucket", prefix("notes/s"))
    assertThat(recursivePartialNameMatch.blobIds).containsExactlyInAnyOrder(
      BlobId.of("my_bucket", "notes/storage/merp.txt", 1),
      BlobId.of("my_bucket", "notes/storage/traif.txt", 1)
    )
  }

  abstract fun newStorageRpc(): T
}
