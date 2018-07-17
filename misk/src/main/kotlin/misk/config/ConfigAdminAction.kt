package misk.config

import misk.environment.Environment
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
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
  // @AdminDashboard will then be able to be picked up by misq
  fun getAll(): Response {
    // TODO(mmihic): Need to figure out how to get the overrides.
    val yamlFiles = MiskConfig.loadConfigYamlMap(appName, environment, listOf())
    val effectiveYaml = MiskConfig.flattenYamlMap(yamlFiles).toString()
    return Response(effective_config = effectiveYaml, yaml_files = yamlFiles)
  }

  data class Response(val effective_config: String, val yaml_files: Map<String, String?>)
}