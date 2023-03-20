package misk.web.metadata.config

import com.google.inject.Inject
import misk.config.AppName
import misk.config.MiskConfig
import misk.resources.ResourceLoader
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.metadata.jvm.JvmMetadataAction
import wisp.config.Config
import wisp.deployment.Deployment
import javax.inject.Singleton

@Singleton
class ConfigMetadataAction @Inject constructor(
  @AppName appName: String,
  deployment: Deployment,
  config: Config,
  private val jvmMetadataAction: JvmMetadataAction,
  private val mode: ConfigTabMode
) : WebAction {
  private val resources: Map<String, String?> = generateConfigResources(appName, deployment, config)

  @Get("/api/config/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    return Response(resources = resources)
  }

  private fun generateConfigResources(
    appName: String,
    deployment: Deployment,
    config: Config
  ): Map<String, String?> {
    // TODO: Need to figure out how to get the overrides (ie. k8s /etc/secrets, database override...).
    val rawYamlFiles = MiskConfig.loadConfigYamlMap(appName, deployment, listOf())

    val configFileContents = linkedMapOf<String, String?>()
    if (mode != ConfigTabMode.SAFE) {
      configFileContents.put("Effective Config", MiskConfig.toRedactedYaml(config, ResourceLoader.SYSTEM))
    }
    if (mode == ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS) {
      rawYamlFiles.forEach { configFileContents.put(it.key, it.value) }
    }
    configFileContents.put("JVM", jvmMetadataAction.getRuntime())

    return configFileContents
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
