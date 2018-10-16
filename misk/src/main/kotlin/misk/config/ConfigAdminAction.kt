package misk.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import misk.config.ConfigAdminAction.Companion.generateConfigResources
import misk.environment.Environment
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.metadata.AdminDashboardAccess
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Singleton
class ConfigAdminAction @Inject constructor(
  val optionalBinder: OptionalBinder
) : WebAction {
  @Get("/api/config/all")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getAll(): Response {
    // TODO(mmihic): Need to figure out how to get the overrides.
    // Regex to match on password values for password redaction in output
    val yamlFilesRegex = Regex("(?<=(password|passphrase): )([^\n]*)")

    return Response(
        resources = redact(optionalBinder.resources, yamlFilesRegex)
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

    fun generateConfigResources(appName: String, environment: Environment): Map<String, String?> {
      val rawYamlFiles = MiskConfig.loadConfigYamlMap(appName, environment, listOf())
      val effectiveConfigJsonString = MiskConfig.flattenYamlMap(rawYamlFiles).toString()
      val effectiveConfigJsonNodeTree = ObjectMapper().readTree(effectiveConfigJsonString)
      val effectiveConfigYaml = YAMLMapper().writeValueAsString(effectiveConfigJsonNodeTree).drop(4)
      val yamlFiles = linkedMapOf<String, String?>("Effective Config" to effectiveConfigYaml)
      rawYamlFiles.map { yamlFiles.put("classpath:/${it.key}", it.value) }
      return yamlFiles
    }
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class ConfigResources

/**
 * https://github.com/google/guice/wiki/FrequentlyAskedQuestions#how-can-i-inject-optional-parameters-into-a-constructor
 */
@Singleton
class OptionalBinder @Inject constructor(
  @AppName val appName: String,
  val environment: Environment
) {
  @com.google.inject.Inject(optional = true)
  @ConfigResources
  var resources: Map<String, String?> = generateConfigResources(appName, environment)
}