package misk.web.metadata.config

import com.google.inject.Inject
import javax.inject.Singleton
import misk.config.AppName
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Env
import misk.environment.Environment
import misk.resources.ResourceLoader
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes

@Singleton
class ConfigMetadataAction @Inject constructor(
  @AppName val appName: String,
  val environment: Environment,
  val config: Config
) : WebAction {
  val resources: Map<String, String?> =
    generateConfigResources(appName, environment,
      config)

  @Get("/api/config/metadata")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    // TODO(mmihic): Need to figure out how to get the overrides.
    // TODO make this config variable specific redaction with an annotation, not just password/passphrase
    // Regex to match on password values for password redaction in output
    val yamlFilesRegex = Regex("(?<=(password|passphrase): )([^\n]*)")

    return Response(
      resources = redact(resources, yamlFilesRegex)
    )
  }

  data class Response(val resources: Map<String, String?>)

  companion object {
    fun redact(
      mapOfOutputs: Map<String, String?>,
      regex: Regex
    ) = mapOfOutputs.mapValues { it.value?.replace(regex, "████████") }

    fun redact(output: String, regex: Regex) =
        output.replace(regex, "████████")

    fun generateConfigResources(
      appName: String,
      environment: Environment,
      config: Config
    ): Map<String, String?> {
      val rawYamlFiles = MiskConfig.loadConfigYamlMap(appName, Env(environment.name), listOf())
      val yamlFiles = linkedMapOf<String, String?>("Effective Config" to MiskConfig.toYaml(
        config, ResourceLoader.SYSTEM))
      rawYamlFiles.map { yamlFiles.put(it.key, it.value) }
      return yamlFiles
    }
  }
}
