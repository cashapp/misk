package misk.cloud.gcp.storage

import com.google.api.services.storage.model.StorageObject
import com.google.cloud.Tuple
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.spi.v1.StorageRpc
import com.google.cloud.storage.spi.v1.StorageRpc.Option.IF_GENERATION_MATCH
import com.google.cloud.storage.spi.v1.StorageRpc.Option.IF_GENERATION_NOT_MATCH
import com.squareup.moshi.Moshi
import misk.io.listRecursively
import misk.moshi.adapter
import misk.okio.forEachBlock
import okio.buffer
import okio.source
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.streams.asSequence

/**
 * Implementation of [StorageRpc] that is backed by local disk storage. Useful for running
 * in development mode on local machines, since there is no GCS emulator available. Files
 * are stored with the generation version appended as an extension suffix, with a symlink
 * existing for the latest generation. To preserve GCS atomicity semantics, progressive
 * uploads are handled by storing the interim uploaded data in a temporary file based
 * off the latest generation, then doing a rename to the new generation once the upload
 * is complete.
 *
 * The implementation uses file locks to support multiple local processes accessing the same
 * storage directory. Whenever a blob is updated, the local store will acquire an exclusive
 * lock on a corresponding lock file, releasing that lock when the update is complete (or when
 * the process dies). It's slightly more complicated since we need to deal with the possibility
 * that writer stops partway through without the process failing; in those cases we don't want to
 * prevent subsequent updates from other processes.
 *
 * Write process:
 *  - on open
 *      - acquire a read lock
 *      - read metadata constraints, and create a new target metadata object for the next generation
 *      - release read lock
 *      - create a new temp file for receiving the update
 *      - generate an upload id, save the temp file location + target metadata + constraints
 *        in-memory associated with the upload id
 *  - on write
 *      - write to the temp file for that upload id
 *  - on finish
 *      - acquire a write lock, read the latest metadata for the blob and re-check constraints
 *        to make sure nothing changed underneath (e.g. a concurrent upload for that blob did
 *        not complete)
 *      - copy from the temp file to a new file in the content directory, appending the
 *        new generation number. This is done as an atomic move + overwrite
 *      - write updated metadata to the metadata file. This is done as an atomic move + overwrite.
 *        Until this is complete, the new version of the content is not accessible to readers.
 *      - release the write lock
 *      - remove the content file for the prior generation
 *
 *  If a writer fails between open and finish, all that happens is we have abandoned temp files
 *  If a writer fails after moving the temp file to the contents directory but before updating
 *    the metadata, then we have a bad content file for that generation sitting in the content
 *    directory. Since the metadata hasn't been updated with the new generation, this content
 *    is not readable, and a subsequent write will overwrite it.
 *  If a writer fails after updating the metadata but before removing the prior generation
 *    content file, we'll have left an abandoned content file. A garbage collection process
 *    can be run to clean these up eventually
 *
 * Read process
 *  - acquire a read lock
 *  - read the metadata for the blob and check constraints (including etag)
 *  - read the raw bytes from the content file
 *  - release the read lock
 *
 * Clients use etags to detect when a blob is updated while a progressive download is in place; the
 * etag returned from a prior read is sent in subsequent reads. We simply use the generation number
 * as the etag value.
 *
 */
class LocalStorageRpc(root: Path, moshi: Moshi = Moshi.Builder().build()) : BaseCustomStorageRpc() {
  // Handles in-process synchronization; cross-process synchronization is handled by file locks
  private val internalLock = ReentrantReadWriteLock()
  private val locksRoot = root.resolve("locks")
  private val metadataRoot = root.resolve("metadata")
  private val contentRoot = root.resolve("content")
  private val uploadRoot = root.resolve("uploads")
  private val metadataAdapter = moshi.adapter<BlobMetadata>()
  private val uploads = mutableMapOf<String, Upload>()

  override fun create(
    obj: StorageObject,
    content: InputStream,
    options: Map<StorageRpc.Option, *>
  ): StorageObject? {
    try {
      val upload = beginUpload(obj, options)
      var destOffset: Long = 0
      val source = content.source().buffer()
      source.forEachBlock(1024) { buffer, bytesRead ->
        write(upload.id, buffer, 0, destOffset, bytesRead, false)
        destOffset += bytesRead
      }
      endUpload(upload)

      return upload.targetMetadata.toStorageObject(obj.bucket, obj.name, destOffset)
    } catch (e: IOException) {
      throw StorageException(e)
    }
  }

  override fun list(
    bucket: String,
    options: Map<StorageRpc.Option, *>
  ): Tuple<String, Iterable<StorageObject>> {
    try {
      val delimiter = options[StorageRpc.Option.DELIMITER]?.toString()
      val prefix = options[StorageRpc.Option.PREFIX]?.toString()?.trimStart('/') ?: ""
      val prefixElements = prefix.split(delimiter ?: "/").toTypedArray()
      val parentFolderElements = prefixElements.dropLast(1).toTypedArray()
      val parentMetadataFolder =
          metadataRoot.resolve(Paths.get(bucket, *parentFolderElements))
      val bucketRoot = metadataRoot.resolve(Paths.get(bucket))
      val filePrefix = prefixElements.last().trim()

      val (folderPaths, filePaths) = if (delimiter != null) {
        // We want to find just the files + subfolders in the current folder
        Files.list(parentMetadataFolder).asSequence()
            .filter {
              filePrefix.isEmpty() || it.fileName.toString().startsWith(filePrefix)
            }
            .partition { Files.isDirectory(it) }
      } else {
        // We want to find all of the files beneath this sub-folder
        listOf<Path>() to parentMetadataFolder.listRecursively().filter {
          filePrefix.isEmpty() || it.getName(parentMetadataFolder.nameCount).toString()
              .startsWith(filePrefix)
        }
      }

      val folders = folderPaths
          .map { bucketRoot.toBlobId(it) }
          .map {
            StorageObject()
                .setBucket(it.bucket)
                .setName(it.name)
          }

      val files = filePaths
          .map { bucketRoot.toBlobId(it) }
          .mapNotNull { blobId ->
            withReadLock(blobId) {
              readMetadata(blobId)?.let {
                val contentPath = contentRoot.resolve(blobId.toPath(it.generation))
                val contentSize = getContentSize(contentPath)
                it.toStorageObject(blobId, contentSize)
              }
            }
          }

      return Tuple.of("", files + folders)
    } catch (e: IOException) {
      throw StorageException(e)
    }
  }

  override fun get(obj: StorageObject, options: Map<StorageRpc.Option, *>): StorageObject? = try {
    withReadLock(obj.blobId) {
      getMetadataForReading(obj.blobId, options)?.let { metadata ->
        val contentPath = contentRoot.resolve(obj.blobId.toPath(metadata.generation))
        val size = getContentSize(contentPath)
        metadata.toStorageObject(obj.bucket, obj.name, size)
      }
    }
  } catch (e: IOException) {
    throw StorageException(e)
  }

  override fun delete(obj: StorageObject, options: Map<StorageRpc.Option, *>): Boolean {
    try {
      return withWriteLock(obj.blobId) {
        // Check metadata constraints. This will throw a StorageException if a
        // constraint fails, which we catch and turn into false return code
        getMetadataForReading(obj.blobId, options)?.let {
          // NB(mmihic): Delete metadata first, this makes the contents inaccessible
          // even if the content file is left in place
          Files.deleteIfExists(metadataRoot.resolve(obj.blobId.toPath()))
          Files.deleteIfExists(contentRoot.resolve(obj.blobId.toPath(it.generation)))
        } ?: false
      }
    } catch (e: StorageException) {
      // One of the metadata constraint checks failed
      return false
    } catch (e: IOException) {
      throw StorageException(e)
    }
  }

  override fun load(obj: StorageObject, options: Map<StorageRpc.Option, *>): ByteArray = try {
    withReadLock(obj.blobId) {
      val metadata = getMetadataForReading(obj.blobId, options)
          ?: throw StorageException(404, "${obj.blobId.fullName} not found")

      Files.readAllBytes(contentRoot.resolve(obj.blobId.toPath(metadata.generation)))
    }
  } catch (e: IOException) {
    throw StorageException(e)
  }

  override fun read(
    from: StorageObject,
    options: Map<StorageRpc.Option, *>,
    zposition: Long,
    zbytes: Int
  ): Tuple<String, ByteArray> = try {
    withReadLock(from.blobId) {
      val metadata = getMetadataForReading(from.blobId, options)
          ?: throw StorageException(404, "${from.blobId.fullName} not found")

      val contentPath = contentRoot.resolve(from.blobId.toPath(metadata.generation))
      val contentChannel = Files.newByteChannel(contentPath, READ)
      val contents = contentChannel.use {
        val toRead = Math.min((it.size() - zposition).toInt(), zbytes)
        val bytes = ByteArray(toRead)
        it.position(zposition)
        it.read(ByteBuffer.wrap(bytes))
        bytes
      }

      Tuple.of(metadata.generation.toString(), contents)
    }
  } catch (e: IOException) {
    throw StorageException(e)
  }

  override fun open(obj: StorageObject, options: Map<StorageRpc.Option, *>): String =
      beginUpload(obj, options).id

  override fun write(
    uploadId: String,
    toWrite: ByteArray,
    toWriteOffset: Int,
    destOffset: Long,
    length: Int,
    last: Boolean
  ) {
    try {
      val upload = continueUpload(uploadId, toWrite, toWriteOffset, destOffset, length)
      if (last) {
        endUpload(upload)
      }
    } catch (e: IOException) {
      throw StorageException(e)
    }
  }

  override fun openRewrite(request: StorageRpc.RewriteRequest): StorageRpc.RewriteResponse {
    return try {
      // NB(mmihic): We have to lock both the source and target in deterministic order
      // regardless of whether they are being used as the source or target, otherwise
      // we risk running into deadlocks
      val (source, target) = request.source to request.target
      val (lock1, lock2) = arrayOf(source.blobId, target.blobId)
          .sortedWith(Comparator { b1, b2 -> b1.compareTo(b2) })

      withWriteLock(lock1) {
        withWriteLock(lock2) {
          val sourceMetadata = getMetadataForReading(source.blobId, request.sourceOptions)
              ?: throw StorageException(404, "${source.blobId.fullName} not found")
          val sourceContentFile =
              contentRoot.resolve(source.blobId.toPath(sourceMetadata.generation))

          val existingTargetMetadata =
              getMetadataForWriting(target.blobId, request.targetOptions)
          val newTargetMetadata =
              request.target.nextGenerationMetadata(existingTargetMetadata)
          val targetContentFile =
              contentRoot.resolve(target.blobId.toPath(newTargetMetadata.generation))

          // Copy from source to a temporary file, then atomically move from the
          // temp file into the target target
          val tempFile = createTempUploadFile(target.blobId)
          Files.copy(sourceContentFile, tempFile, REPLACE_EXISTING)
          Files.move(tempFile, targetContentFile, ATOMIC_MOVE, REPLACE_EXISTING)
          writeMetadata(target.blobId, newTargetMetadata)

          existingTargetMetadata?.let {
            val oldTargetContentPath =
                contentRoot.resolve(target.blobId.toPath(it.generation))
            try {
              Files.deleteIfExists(oldTargetContentPath)
            } catch (_: IOException) {
              // This is ok
            }
          }

          val sourceContentSize = getContentSize(sourceContentFile)
          StorageRpc.RewriteResponse(
              request,
              request.target,
              sourceContentSize,
              true,
              "token",
              sourceContentSize)
        }
      }
    } catch (e: IOException) {
      throw StorageException(e)
    }
  }

  private fun writeMetadata(blobId: BlobId, metadata: BlobMetadata) {
    // NB(mmihic): Must be called with a write lock
    val metadataJson = metadataAdapter.toJson(metadata).toByteArray()

    // Write as a temp file, and atomically move to the final destination. This way
    // a failure of the process won't correupt the metadata file
    val tempMetadataPath = uploadRoot.resolve("_metadata").resolve(blobId.toPath())
    Files.createDirectories(tempMetadataPath.parent)
    Files.write(tempMetadataPath, metadataJson, CREATE, WRITE, TRUNCATE_EXISTING)

    val metadataPath = metadataRoot.resolve(blobId.toPath())
    Files.createDirectories(metadataPath.parent)
    Files.move(tempMetadataPath, metadataPath, ATOMIC_MOVE, REPLACE_EXISTING)
  }

  private fun readMetadata(blobId: BlobId): BlobMetadata? {
    // NB(mmihic): Must be called with a read or write lock
    val metadataPath = metadataRoot.resolve(blobId.toPath())
    Files.createDirectories(metadataPath.parent)

    return if (Files.exists(metadataPath)) {
      val metadataContent = String(Files.readAllBytes(metadataPath))
      metadataAdapter.fromJson(metadataContent)
    } else null
  }

  private fun getContentSize(contentPath: Path) = try {
    Files.newByteChannel(contentPath, READ).use { it.size() }
  } catch (e: FileNotFoundException) {
    0L
  }

  private fun getMetadataForWriting(
    blobId: BlobId,
    options: Map<StorageRpc.Option, *>
  ): BlobMetadata? {
    val existingMetadata = readMetadata(blobId)
    options.generationMatch?.let {
      when {
        it == 0L && existingMetadata != null ->
          throw StorageException(401, "${blobId.fullName} already exists")
        it != 0L && existingMetadata == null ->
          throw StorageException(404, "${blobId.fullName} does not exist")
        it != 0L && existingMetadata != null && it != existingMetadata.generation ->
          throw StorageException(401,
              "generation mismatch: ${existingMetadata.generation} != $it")
        else -> {
        }
      }
    }

    return existingMetadata
  }

  private fun getMetadataForReading(
    blobId: BlobId,
    options: Map<StorageRpc.Option, *>
  ): BlobMetadata? {
    val metadata = readMetadata(blobId) ?: return null
    options.generationMatch?.let {
      if (metadata.generation != it) {
        throw StorageException(401, "generation mismatch: ${metadata.generation} != $it")
      }
    }

    options.generationNotMatch?.let {
      if (metadata.generation == it) {
        throw StorageException(401, "generation mismatch: ${metadata.generation} == $it")
      }
    }

    return metadata
  }

  private fun beginUpload(obj: StorageObject, options: Map<StorageRpc.Option, *>): Upload {
    try {
      val newMetadata = withReadLock(obj.blobId) {
        // Check constraint on existing metadata and return the metadata that will
        // apply once the upload has completed.
        obj.nextGenerationMetadata(getMetadataForWriting(obj.blobId, options))
      }

      // Create a temporary file to hold the upload contents.
      val uploadId = UUID.randomUUID().toString()
      val uploadPath = createTempUploadFile(obj.blobId)

      // Save off the upload information for subsequent writes
      val upload = Upload(uploadId, obj.blobId, uploadPath, newMetadata)
      internalLock.write {
        uploads[uploadId] = upload
      }

      return upload
    } catch (e: IOException) {
      throw StorageException(e)
    }
  }

  private fun continueUpload(
    uploadId: String,
    toWrite: ByteArray,
    toWriteOffset: Int,
    destOffset: Long,
    length: Int
  ): Upload = try {
    // Copy bytes into the temporary file for this upload
    internalLock.read {
      val upload = uploads[uploadId]
          ?: throw StorageException(404, "no such upload $uploadId")
      val buffer = ByteBuffer.wrap(toWrite, toWriteOffset, length)
      val ch = Files.newByteChannel(upload.tempFile, CREATE, WRITE)
      val position = Math.min(destOffset, ch.size())
      ch.use {
        it.position(position)
        it.write(buffer)
      }
      upload
    }
  } catch (e: IOException) {
    throw StorageException(e)
  }

  private fun endUpload(upload: Upload) {
    try {
      internalLock.write {
        // Clear out the internal upload tracking
        uploads.remove(upload.blobId.fullName)
      }

      withWriteLock(upload.blobId) {
        // Move the upload file into the content directory tagged with the new generation,
        // overwriting any version that might've been left by a previously failed uplaod
        val newGeneration = upload.targetMetadata.generation
        val newContentPath = contentRoot.resolve(upload.blobId.toPath(newGeneration))
        Files.createDirectories(newContentPath.parent)
        Files.move(upload.tempFile, newContentPath, ATOMIC_MOVE, REPLACE_EXISTING)

        // Update the metadata to point to the new generation. If we fail between the
        // prior step and this one, there is no harm - the new content will have
        // been laid down but won't be accessible because the metadata doesn't point
        // to it, and the next upload will overwrite it
        writeMetadata(upload.blobId, upload.targetMetadata)

        // Delete the content file for the previous generation. If we fail between
        // the prior step and this one, there is no harm - we'll leave old content
        // files around, but won't use them since the metadata has advanced. We
        // could clean these up via a garbage collection process somewhere down the line
        val oldGeneration = upload.targetMetadata.generation - 1
        val oldContentPath = contentRoot.resolve(upload.blobId.toPath(oldGeneration))
        Files.deleteIfExists(oldContentPath)
      }
    } catch (e: IOException) {
      throw StorageException(e)
    }
  }

  private fun createTempUploadFile(blobId: BlobId): Path {
    val blobPath = blobId.toPath()
    val uploadFolder = uploadRoot.resolve(blobPath).parent
    Files.createDirectories(uploadFolder)

    val uploadPath = Files.createTempFile(uploadFolder, blobPath.fileName.toString(), "")
    Files.createDirectories(uploadPath.parent)
    return uploadPath
  }

  private fun <T> withReadLock(blobId: BlobId, f: () -> T): T = internalLock.read {
    withFileLock(blobId, true, f)
  }

  private fun <T> withWriteLock(blobId: BlobId, f: () -> T): T = internalLock.write {
    withFileLock(blobId, false, f)
  }

  private fun <T> withFileLock(blobId: BlobId, shared: Boolean, f: () -> T): T {
    val lockPath = locksRoot.resolve(blobId.toPath())
    Files.createDirectories(lockPath.parent)

    try {
      Files.createFile(lockPath)
    } catch (_: FileAlreadyExistsException) {
    }

    return FileChannel.open(lockPath, if (shared) READ else WRITE).withLock(shared) { f() }
  }

  private class BlobMetadata(
    val generation: Long,
    val metageneration: Long,
    val userProperties: Map<String, String>,
    val contentType: String?,
    val contentEncoding: String?
  ) {
    fun toStorageObject(blobId: BlobId, size: Long = 0): StorageObject =
        StorageObject()
            .setGeneration(generation)
            .setName(blobId.name)
            .setBucket(blobId.bucket)
            .setMetageneration(metageneration)
            .setContentType(contentType)
            .setContentEncoding(contentEncoding)
            .setSize(BigInteger.valueOf(size))
            .setMetadata(if (userProperties.isEmpty()) null else userProperties)

    fun toStorageObject(bucket: String, name: String, size: Long = 0) =
        toStorageObject(BlobId.of(bucket, name), size)
  }

  private class Upload(
    val id: String,
    val blobId: BlobId,
    val tempFile: Path,
    val targetMetadata: BlobMetadata
  )

  /** @return a new version of the given metadata, updated based on the storage object */
  private fun StorageObject.nextGenerationMetadata(existing: BlobMetadata?): BlobMetadata {
    val newContentType = contentType ?: existing?.contentType
    val newContentEncoding = contentEncoding ?: existing?.contentEncoding
    val newUserProperties = metadata ?: existing?.userProperties ?: mapOf()
    val newGeneration = (existing?.generation ?: 0L) + 1
    val newMetaGeneration = when {
      existing == null -> 1
      existing.userProperties != newUserProperties -> existing.metageneration + 1
      else -> existing.metageneration
    }

    return BlobMetadata(
        newGeneration,
        newMetaGeneration,
        newUserProperties,
        newContentType,
        newContentEncoding)
  }
}

private val Map<StorageRpc.Option, *>.generationNotMatch
  get() = (this[IF_GENERATION_NOT_MATCH] as? Number)?.toLong()

private val Map<StorageRpc.Option, *>.generationMatch
  get() = (this[IF_GENERATION_MATCH] as? Number)?.toLong()

private val StorageObject.blobId get() = BlobId.of(bucket, name, generation)

private fun BlobId.toPath(generation: Long) =
    Paths.get(bucket, *parentPathElements, fileName(generation))

private fun BlobId.toPath() = Paths.get(bucket, *pathElements)
private val BlobId.parentPathElements get() = pathElements.dropLast(1).toTypedArray()
private val BlobId.pathElements get() = name.split('/').toTypedArray()
private fun BlobId.fileName(generation: Long = 1) = "${pathElements.last()}.$generation"
private val BlobId.fullName get() = "$bucket:$name"

private fun Path.toBlobId(childPath: Path): BlobId =
    BlobId.of(fileName.toString(), relativize(childPath).joinToString("/"))

fun <T> FileChannel.withLock(shared: Boolean, action: () -> T) =
    lock(0, Long.MAX_VALUE, shared).use { action() }
