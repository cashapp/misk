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
  private val mode: ConfigTabMode,
) : WebAction {
  private val resources: Map<String, String?> =
    generateConfigResources(appName, deployment, config)

  @Get("/api/config/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    // TODO move this redacting to happen on class initialization
    // TODO(mmihic): Need to figure out how to get the overrides.
    // TODO make this config variable specific redaction with an annotation, not just password/passphrase
    // Regex to match on password values for password redaction in output
    val yamlFilesRegex = Regex("(?<=(password|passphrase): )([^\n]*)")

    return Response(
      resources = redact(resources, yamlFilesRegex)
    )
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
        rawYamlFiles.map { configFileContents.put(it.key, it.value) }
        configFileContents.put("JVM", jvmMetadataAction.getRuntime())
      }
    }
    return configFileContents
  }

  data class Response(val resources: Map<String, String?>)

  companion object {
    fun redact(
      mapOfOutputs: Map<String, String?>,
      regex: Regex
    ) = mapOfOutputs.mapValues { it.value?.replace(regex, "████████") }

    fun redact(output: String, regex: Regex) =
      output.replace(regex, "████████")
  }

  enum class ConfigTabMode {
    SAFE, // Only show safe content which will not leak Misk secrets
    UNSAFE_LEAK_MISK_SECRETS
  }
}
