package misk.web.metadata.config

import com.google.inject.Provider
import jakarta.inject.Inject
import misk.config.AppName
import misk.config.MiskConfig
import misk.resources.ResourceLoader
import misk.web.metadata.Metadata
import misk.web.metadata.jvm.JvmMetadataAction
import wisp.deployment.Deployment

data class ConfigMetadata(
  val resources: Map<String, String?>
) : Metadata(id = "config", metadata = resources)

class ConfigMetadataProvider : Provider<ConfigMetadata> {
  @Inject @AppName private lateinit var appName: String
  @Inject private lateinit var deployment: Deployment
  @Inject private lateinit var config: wisp.config.Config
  @Inject private lateinit var jvmMetadataAction: JvmMetadataAction
  @Inject private lateinit var mode: ConfigMetadataAction.ConfigTabMode

  override fun get() = ConfigMetadata(
    resources = generateConfigResources(appName, deployment, config)
  )

  private fun generateConfigResources(
    appName: String,
    deployment: Deployment,
    config: wisp.config.Config
  ): Map<String, String?> {
    // TODO: Need to figure out how to get the overrides (ie. k8s /etc/secrets, database override...).
    val rawYamlFiles = MiskConfig.loadConfigYamlMap(appName, deployment, listOf())

    val configFileContents = linkedMapOf<String, String?>()
    if (mode != ConfigMetadataAction.ConfigTabMode.SAFE) {
      configFileContents.put(
        "Effective Config",
        MiskConfig.toRedactedYaml(config, ResourceLoader.SYSTEM)
      )
    }
    if (mode == ConfigMetadataAction.ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS) {
      rawYamlFiles.forEach { configFileContents.put(it.key, it.value) }
    }
    configFileContents.put("JVM", jvmMetadataAction.getRuntime())

    return configFileContents
  }
}
