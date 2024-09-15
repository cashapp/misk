package misk.web.metadata.all

import jakarta.inject.Inject
import com.google.inject.Provider
import jakarta.inject.Singleton
import misk.time.timed
import misk.web.Get
import misk.web.PathParam
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.metadata.Metadata
import wisp.logging.getLogger

@Singleton
class AllMetadataAction @Inject constructor(
  /** Uses a provider here so every load gets fresh metadata from the Metadata provider. */
  private val allMetadata: Map<String, @JvmSuppressWildcards Provider<Metadata>>
) : WebAction {
  /** Metadata keys won't change while a service is running so they can be cached safely. */
  internal val allKeys by lazy { allMetadata.keys.sorted() }

  @JvmOverloads
  @Get(PATH)
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AllMetadataAccess
  fun getAll(@PathParam id: String? = null): Response {
    val (duration, all) = timed {
      if (id == "all") {
        // Return all metadata if the id is "all"
        allMetadata.mapValues { it.timedGet() }
      } else {
        // Return metadata for the requested id
        allMetadata.filter { it.key == id }.mapValues { it.timedGet() }
      }
    }
    val durationMillis = duration.toMillis()
    if (durationMillis > 10) {
      logger.info { "Metadata [id=$id] took [loadTimeMs=${durationMillis}ms]." }
    }
    return Response(all = all)
  }

  data class Response(val all: Map<String, Metadata>)

  private fun Map.Entry<String, @JvmSuppressWildcards Provider<Metadata>>.timedGet(): Metadata {
    val (duration, metadata) = timed {
      value.get()
    }
    val durationMillis = duration.toMillis()
    if (durationMillis > 10) {
      logger.info("MetadataProvider [key=$key] took [loadTimeMs=${durationMillis}ms] to load [sizeChars=${metadata.toString().length}] by [class=${value}].")
    }
    return metadata
  }

  companion object {
    const val PATH = "/api/{id}/metadata"

    private val logger = getLogger<AllMetadataAction>()
  }
}
