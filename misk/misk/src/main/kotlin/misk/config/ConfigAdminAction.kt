package misk.config

import com.google.inject.Inject
import misk.environment.Environment
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.metadata.AdminDashboardAccess
import javax.inject.Singleton

@Singleton
class ConfigAdminAction @Inject constructor(
  @AppName val appName: String,
  val environment: Environment,
  val config: Config
) : WebAction {
  val resources: Map<String, String?> = generateConfigResources(appName, environment, config)

  @Get("/api/config/all")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    // TODO(mmihic): Need to figure out how to get the overrides.
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
      val rawYamlFiles = MiskConfig.loadConfigYamlMap(appName, environment, listOf())
      val yamlFiles = linkedMapOf<String, String?>("Effective Config" to MiskConfig.toYaml(config))
      rawYamlFiles.map { yamlFiles.put("classpath:/${it.key}", it.value) }
      return yamlFiles
    }
  }
}
