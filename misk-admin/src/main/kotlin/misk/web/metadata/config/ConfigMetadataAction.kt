package misk.web.metadata.config

import com.google.inject.Provider
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes

@Singleton
class ConfigMetadataAction @Inject constructor(
  private val metadataProvider: Provider<ConfigMetadata>
) : WebAction {
  @Get("/api/v1/config/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    return Response(resources = metadataProvider.get().resources)
  }

  data class Response(val resources: Map<String, String?>)

  enum class ConfigTabMode {
    /** Only show safe content which will not leak Misk secrets */
    SAFE,
    /**
     * Show redacted effective config loaded into application, risk of leak if sensitive
     * non-Secret fields don't have @misk.config.Redact annotation manually added.
     */
    SHOW_REDACTED_EFFECTIVE_CONFIG,
    /** Shows all possible resources, YAML files are not redacted */
    UNSAFE_LEAK_MISK_SECRETS
  }
}
