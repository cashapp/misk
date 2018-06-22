package misk.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import misk.environment.Environment
import misk.logging.getLogger
import okio.Okio
import java.net.URL

object MiskConfig {
  internal val logger = getLogger<Config>()

  @JvmStatic
  inline fun <reified T : Config> load(appName: String, environment: Environment): T {
    return load(T::class.java, appName, environment)
  }

  @JvmStatic
  fun <T : Config> load(
    configClass: Class<out Config>,
    appName: String,
    environment: Environment
  ): T {
    val mapper = ObjectMapper(YAMLFactory()).registerModules(KotlinModule(), JavaTimeModule())

    val configYamls = loadConfigYamlMap(appName, environment)

    check(configYamls.values.any { it != null }) {
      "could not find configuration files - checked ${configYamls.keys}"
    }

    val jsonNode = flattenYamlMap(configYamls)

    @Suppress("UNCHECKED_CAST")
    try {
      return mapper.readValue(jsonNode.toString(), configClass) as T
    } catch (e: MissingKotlinParameterException) {
      throw IllegalStateException(
          "could not find $appName $environment configuration for ${e.parameter.name}", e)
    } catch (e: Exception) {
      throw IllegalStateException("failed to load configuration for $appName $environment", e)
    }
  }

  /**
   * Returns a JsonNode that combines the YAMLs in `configYamls`. If two nodes define the
   * same value the last one wins.
   */
  fun flattenYamlMap(configYamls: Map<String, String?>): JsonNode {
    val mapper = ObjectMapper(YAMLFactory()).registerModules(KotlinModule(), JavaTimeModule())
    var result = mapper.createObjectNode()

    for ((key, value) in configYamls) {
      if (value == null) continue
      try {
        result = mapper.readerForUpdating(result).readValue(value)
      } catch (e: Exception) {
        throw IllegalStateException("could not parse $key: ${e.message}", e)
      }
    }
    return result
  }

  /**
   * Returns a map whose keys are the names of the source Yaml files to load, and
   * whose values are the contents of those files. If a file is absent the map’s value
   * will be null.
   */
  fun loadConfigYamlMap(
    appName: String,
    environment: Environment
  ): Map<String, String?> {
    val result = mutableMapOf<String, String?>()

    for (configFileName in configFileNames(appName, environment)) {
      val url = getResource(Config::class.java, configFileName)
      result[configFileName] = url?.readUtf8()
    }
    return result
  }

  private fun URL.readUtf8(): String {
    return openStream().use {
      Okio.buffer(Okio.source(it)).readUtf8()
    }
  }

  /** @return the list of config file names in the order they should be read */
  private fun configFileNames(appName: String, environment: Environment): List<String> =
      listOf("common", environment.name.toLowerCase()).map { "$appName-$it.yaml" }

  private fun getResource(
    configClass: Class<out Config>,
    fileName: String
  ): URL? = configClass.classLoader.getResource(fileName)
}
