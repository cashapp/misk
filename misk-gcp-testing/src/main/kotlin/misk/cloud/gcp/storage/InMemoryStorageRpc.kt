package misk.cloud.gcp.storage

import com.google.api.services.storage.model.StorageObject
import com.google.cloud.Tuple
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.spi.v1.StorageRpc
import com.google.common.io.ByteStreams.toByteArray
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.file.FileAlreadyExistsException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Implementation of [StorageRpc] that keeps all of its data purely in-memory, useful primarily
 * for tests. This implementation is fully thread safe.
 */
@Deprecated("Replace the dependency on misk-gcp-testing with testFixtures(misk-gcp)")
class InMemoryStorageRpc : BaseCustomStorageRpc() {
  private val content = mutableMapOf<String, ByteArray>()
  private val metadata = mutableMapOf<String, StorageObject>()
  private val pendingContent = mutableMapOf<String, ByteArray>()
  private val lock = ReentrantReadWriteLock()

  override fun create(
    obj: StorageObject,
    content: InputStream,
    options: Map<StorageRpc.Option, *>
  ): StorageObject? = lock.write {
    val existing = options.check(metadata[obj.path])
    val newGeneration = (existing?.generation ?: 0) + 1
    val existingMetadata = existing?.metadata
    val newMetaGeneration = when {
      existingMetadata == null -> 1
      existingMetadata != obj.metadata -> existing.metageneration + 1
      else -> existing.metageneration
    }

    val contentBytes = toByteArray(content)
    this.content[obj.path] = contentBytes

    val newStorageObject = obj.clone()
    newStorageObject.generation = newGeneration
    newStorageObject.metageneration = newMetaGeneration
    newStorageObject.setSize(BigInteger.valueOf(contentBytes.size.toLong()))
    metadata[obj.path] = newStorageObject
    newStorageObject
  }

  override fun list(
    bucket: String,
    options: Map<StorageRpc.Option, *>
  ): Tuple<String, Iterable<StorageObject>> = lock.read {
    val prefix = options[StorageRpc.Option.PREFIX]?.toString()?.trimStart('/') ?: ""

    val matchingObjects = metadata.values.filter {
      it.bucket == bucket && it.name.startsWith(prefix)
    }

    val delimiter = options[StorageRpc.Option.DELIMITER]?.toString()
    val results = matchingObjects.map { obj ->
      delimiter?.let {
        // If the storage object has a folder delimiter after the prefix, we want to take
        // just the initial folder name rather than the object iself
        val subFolderEnd = obj.name.indexOf(delimiter, prefix.length)
        if (subFolderEnd != -1) {
          StorageObject()
            .setName(obj.name.substring(0, subFolderEnd))
            .setBucket(obj.bucket)
            .setOwner(obj.owner)
        } else obj
      } ?: obj
    }.distinctBy { it.name }
    return Tuple.of(null, results)
  }

  override fun get(obj: StorageObject, options: Map<StorageRpc.Option, *>): StorageObject? =
    lock.read {
      val existing = options.check(metadata[obj.path]) ?: return null
      val toReturn = existing.clone()
      toReturn.id = obj.path
      toReturn.setSize(BigInteger.valueOf(content[obj.path]?.size?.toLong() ?: 0L))
    }

  override fun delete(obj: StorageObject, options: Map<StorageRpc.Option, *>): Boolean =
    lock.write {
      try {
        options.check(metadata[obj.path])
        content.remove(obj.path)
        metadata.remove(obj.path) != null
      } catch (e: StorageException) {
        false
      }
    }

  override fun load(obj: StorageObject, options: Map<StorageRpc.Option, *>): ByteArray =
    lock.read {
      options.check(metadata[obj.path])
      return content[obj.path]
        ?: throw StorageException(404, "file not found ${obj.path}")
    }

  override fun read(
    from: StorageObject,
    options: Map<StorageRpc.Option, *>,
    zposition: Long,
    outputStream: OutputStream
  ): Long = lock.read {
    options.check(metadata[from.path])
    val bytes = content[from.path]
      ?: throw StorageException(404, "file not found ${from.path}")

    val position = if (zposition >= 0) zposition.toInt() else 0
    val amtRead = bytes.size - position
    if (amtRead <= 0) return 0L
    val result = bytes.copyOfRange(position, position + amtRead)
    outputStream.write(result)
    return amtRead.toLong()
  }

  override fun open(obj: StorageObject, options: Map<StorageRpc.Option, *>): String = lock.write {
    val existing = options.check(metadata[obj.path])

    val newObject = obj.clone()
    newObject.generation = existing?.generation ?: 0L
    newObject.metageneration = existing?.generation ?: 0L
    metadata[obj.path] = newObject
    return obj.path
  }

  override fun write(
    uploadId: String,
    toWrite: ByteArray,
    toWriteOffset: Int,
    destOffset: Long,
    length: Int,
    last: Boolean
  ) {
    lock.write {
      val destBytes = pendingContent[uploadId]?.let { currentBytes ->
        if (currentBytes.size < length + destOffset) {
          // TODO(mmihic): WTF no bulk copy?
          val newBytes = ByteArray(length + destOffset.toInt())
          for (i in (0 until currentBytes.size)) newBytes[i] = currentBytes[i]
          newBytes
        } else currentBytes
      } ?: ByteArray(length + destOffset.toInt())

      for (i in (0 until length)) destBytes[i + destOffset.toInt()] = toWrite[i + toWriteOffset]
      if (last) {
        content[uploadId] = destBytes
        pendingContent.remove(uploadId)

        metadata[uploadId]?.let {
          val newObject = it.clone()
          newObject.generation++
          metadata[uploadId] = newObject
        }
      } else {
        pendingContent.put(uploadId, destBytes)
      }
    }
  }

  override fun openRewrite(request: StorageRpc.RewriteRequest): StorageRpc.RewriteResponse =
    lock.write {
      request.sourceOptions.check(metadata[request.source.path])
        ?: throw StorageException(404, "file not found ${request.source.path}")

      val existingTarget = request.targetOptions.check(metadata[request.target.path])
      val newTarget = request.target.clone()
      newTarget.generation = (existingTarget?.generation ?: 0) + 1
      newTarget.metageneration = when {
        existingTarget == null -> 1
        existingTarget.metadata == newTarget.metadata -> existingTarget.metageneration
        else -> existingTarget.metageneration + 1
      }

      val sourceData = content[request.source.path]
        ?: throw StorageException(404, "file not found ${request.source.path}")

      metadata[request.target.path] = newTarget
      content[request.target.path] = sourceData.copyOf()
      return StorageRpc.RewriteResponse(
        request,
        request.target,
        sourceData.size.toLong(),
        true,
        "token",
        sourceData.size.toLong()
      )
    }
}

fun Map<StorageRpc.Option, *>.check(obj: StorageObject?): StorageObject? {
  forEach {
    when (it.key) {
      StorageRpc.Option.IF_GENERATION_MATCH ->
        checkMatch(it.value, obj?.generation)
      StorageRpc.Option.IF_GENERATION_NOT_MATCH ->
        checkDoesNotMatch(it.value, obj?.generation)
      StorageRpc.Option.IF_METAGENERATION_MATCH ->
        checkMatch(it.value, obj?.metageneration)
      StorageRpc.Option.IF_METAGENERATION_NOT_MATCH ->
        checkDoesNotMatch(it.value, obj?.metageneration)
      else -> {
      }
    }
  }

  return obj
}

private fun checkMatch(value: Any?, objValue: Long?) {
  val longValue = (value as? Number)?.toLong() ?: 0L
  if (objValue == null) {
    if (longValue != 0L) {
      throw StorageException(FileAlreadyExistsException("already exists"))
    }
  } else if (longValue != objValue) {
    throw StorageException(401, "generation mismatch; $longValue != $objValue")
  }
}

private fun checkDoesNotMatch(value: Any?, objValue: Long?) {
  val longValue = (value as? Number)?.toLong() ?: 0L
  if (objValue == null) {
    if (longValue == 0L) {
      throw StorageException(404, "file not found")
    }
  } else if (longValue == objValue) {
    throw StorageException(401, "generation mismatch; $longValue == $objValue")
  }
}

private val StorageObject.path get() = "$bucket/$name"
