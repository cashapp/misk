package misk.web.metadata.config

import jakarta.inject.Inject
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.p
import kotlinx.html.span
import misk.config.AppName
import misk.config.MiskConfig
import misk.resources.ResourceLoader
import misk.tailwind.components.CodeBlock
import misk.tailwind.components.ToggleContainer
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.config.Config
import wisp.deployment.Deployment

internal data class ConfigMetadata(
  val resources: Map<String, String?>
) : Metadata(
  metadata = resources,
  prettyPrint = resources.entries
    .joinToString("\n\n") {
      "${it.key}\n---\n${it.value?.removePrefix("---")?.removePrefix("\n")}"
    },
) {
  override fun descriptionBlock(tagConsumer: TagConsumer<*>) = tagConsumer.apply {
    ToggleContainer(buttonText = "Documentation", menuBlock = {
      div {
        +"Raw YAML configuration files and the merged Effective Config which the service is currently running on."
      }
    }) {
      div("my-4") {
        h2("text-2xl font-bold") { +"""Documentation""" }

        div("my-6") {
          h3("text-xl font-bold my-4") { +"""1. Only see JVM info? Want to see runtime config or raw YAML? Change your ConfigTabMode.""" }

          p {
            span("font-mono") { +"""ConfigTabMode.SAFE""" }
            +""": Only JVM config"""
          }
          p {
            span("font-mono") { +"""ConfigTabMode.SHOW_REDACTED_EFFECTIVE_CONFIG""" }
            +""": Show JVM and runtime config with secrets redacted"""
          }
          p("mb-4") {
            span("font-mono") { +"""ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS""" }
            +""": Show JVM, redacted runtime config, and raw unredacted YAML files"""
          }
          CodeBlock(
            """// ExemplarService.kt
              |
              |fun main(args: Array<String>) {
              |  val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
              |  val config = MiskConfig.load<ExemplarConfig>("exemplar", deployment)
              |  MiskApplication(
              |    ConfigModule.create("exemplar", config),
              |    ...
              |    AdminDashboardModule(
              |      isDevelopment = !deployment.isReal,
              |-     configTabMode = ConfigMetadataAction.ConfigTabMode.SAFE
              |+     configTabMode = ConfigMetadataAction.ConfigTabMode.SHOW_REDACTED_EFFECTIVE_CONFIG
              |    )
              |  ).run(args)
              |}""".trimMargin()
          )
        }
        div("my-6") {
          h3("text-xl font-bold my-4") { +"""2. Seeing sensitive information that should be redacted? Use Secret<*> or add @Redact.""" }
          CodeBlock(
            """// ExemplarConfig.kt
              |
              |+ import misk.config.Redact
              |+ import misk.config.Secret
              |
              |  data class ExemplarConfig(
              |// Redact using a Secret with sensitive data stored in a resource (classpath, filesystem...)
              |-   val apiKey: String,
              |+   val apiKey: Secret<String>,
              |    val web: WebConfig,
              |    val prometheus: PrometheusConfig,
              |    // Redact a single field
              |+   @Redact
              |    val password: String,
              |    val keys: KeysConfig,
              |  ) : Config
              |
              |// Redact entire class
              |+ @Redact
              |  data class KeysConfig(
              |    val key1: String,
              |    val key2: String,
              |  )""".trimMargin()
          )
        }
      }
    }
  }
}

internal class ConfigMetadataProvider : MetadataProvider<ConfigMetadata> {
  @Inject @AppName private lateinit var appName: String
  @Inject private lateinit var deployment: Deployment
  @Inject private lateinit var config: Config
  @Inject private lateinit var mode: ConfigMetadataAction.ConfigTabMode

  override val id: String = "config"

  override fun get() = ConfigMetadata(
    resources = generateConfigResources(appName, deployment, config)
  )

  private fun generateConfigResources(
    appName: String,
    deployment: Deployment,
    config: Config
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

    return configFileContents
  }
}
