package misk.web.metadata.config

import com.squareup.moshi.JsonAdapter
import jakarta.inject.Inject
import misk.config.AppName
import misk.config.MiskConfig
import misk.resources.ResourceLoader
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.jvm.JvmMetadataAction
import wisp.deployment.Deployment
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

data class ConfigMetadata(
  override val metadata: Map<String, String?>,
  override val adapter: JsonAdapter<Map<String, String?>> = defaultKotlinMoshi.adapter<Map<String, String?>>()
) : Metadata<Map<String, String?>>

class ConfigMetadataProvider : MetadataProvider<Map<String, String?>, ConfigMetadata> {
  @Inject @AppName private lateinit var appName: String
  @Inject private lateinit var deployment: Deployment
  @Inject private lateinit var config: wisp.config.Config
  @Inject private lateinit var jvmMetadataAction: JvmMetadataAction
  @Inject private lateinit var mode: ConfigMetadataAction.ConfigTabMode

  override val id: String = "config"

  override fun get() = ConfigMetadata(
    metadata = generateConfigResources(appName, deployment, config)
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
