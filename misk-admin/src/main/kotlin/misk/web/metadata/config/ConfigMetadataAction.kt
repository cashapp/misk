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
import javax.inject.Qualifier
import javax.inject.Singleton

@Singleton
class ConfigMetadataAction @Inject constructor(
  @AppName appName: String,
  deployment: Deployment,
  config: Config,
  private val jvmMetadataAction: JvmMetadataAction,
  private val mode: ConfigTabMode,
  @ConfigRedactKey keysToRedact: List<String>
) : WebAction {
  private fun redact(
    mapOfOutputs: Map<String, String?>,
    regex: Regex
  ) = mapOfOutputs
    .mapValues { it.value?.replace(regex, "████████") }

  // TODO make this config variable specific redaction with an annotation on config field, not just multibound list of keys
  // Regex to match on redacted keys for redaction in output
  private val yamlFilesRegex = Regex("(?<=(${keysToRedact.joinToString("|")}): )([^\n]*)")
  private val resources: Map<String, String?> =
    redact(generateConfigResources(appName, deployment, config), yamlFilesRegex)

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
    val rawYamlFiles = MiskConfig.loadConfigYamlMap(appName, deployment, listOf())
    val configFileContents = linkedMapOf<String, String?>()
    when (mode) {
      ConfigTabMode.SAFE -> {
        configFileContents.put("Effective Config", MiskConfig.toRedactedYaml(config, ResourceLoader.SYSTEM))
        configFileContents.put("JVM", jvmMetadataAction.getRuntime())
      }
      ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS -> {
        configFileContents.put("Effective Config", MiskConfig.toRedactedYaml(config, ResourceLoader.SYSTEM))
        rawYamlFiles.forEach { configFileContents.put(it.key, it.value) }
        configFileContents.put("JVM", jvmMetadataAction.getRuntime())
      }
    }
    return configFileContents
  }

  data class Response(val resources: Map<String, String?>)

  enum class ConfigTabMode {
    SAFE, // Only show safe content which will not leak Misk secrets
    UNSAFE_LEAK_MISK_SECRETS
  }
}

/**
 * Used to identify an injected list of string keys that should be redacted in Config tab.
 *
 * ```
 * multibind<String, ConfigRedactKey>().toInstance("customKeyToRedact")
 * ```
 */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class ConfigRedactKey
