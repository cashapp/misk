package misk.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import misk.environment.Environment
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.metadata.AdminDashboardAccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigAdminAction : WebAction {
  @Inject @AppName lateinit var appName: String
  @Inject lateinit var environment: Environment

  @Get("/api/config/all")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO(adrw) create new @AdminDashboard annotation because this will fail since there is no @Access
  // @AdminDashboard will then be able to be picked up by skim
  @AdminDashboardAccess
  fun getAll(): Response {
    // TODO(mmihic): Need to figure out how to get the overrides.
    val rawYamlFiles = MiskConfig.loadConfigYamlMap(appName, environment, listOf())
    val effectiveConfigJsonString = MiskConfig.flattenYamlMap(rawYamlFiles).toString()
    val effectiveConfigJsonNodeTree = ObjectMapper().readTree(effectiveConfigJsonString)
    val effectiveConfigYaml = YAMLMapper().writeValueAsString(effectiveConfigJsonNodeTree).drop(4)
    val yamlFiles = linkedMapOf<String, String?>("Effective Config" to effectiveConfigYaml)
    rawYamlFiles.map { yamlFiles.put("classpath:/${it.key}", it.value) }

    // Regex to match on password values for password redaction in output
    val yamlFilesRegex = Regex("(?<=(password|passphrase): )([^\n]*)")

    return Response(
        resources = redact(yamlFiles, yamlFilesRegex)
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
  }
}