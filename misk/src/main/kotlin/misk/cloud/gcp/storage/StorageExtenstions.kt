package misk.cloud.gcp.storage

import com.google.api.gax.paging.Page
import com.google.cloud.storage.Blob

/** @return a list containing all of the elements in this page */
fun <T> Page<T>.toList(): List<T> = values.asSequence().toList()

/** @return a list containing just the ids of the blobs in the page */
val Page<Blob>.blobIds get() = toList().map { it.blobId }

