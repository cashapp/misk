package misk.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import misk.environment.Environment
import misk.logging.getLogger
import okio.buffer
import okio.source
import java.io.File
import java.io.FilenameFilter
import java.net.URL

object MiskConfig {
  internal val logger = getLogger<Config>()

  @JvmStatic
  inline fun <reified T : Config> load(
    appName: String,
    environment: Environment,
    overrideFiles: List<File> = listOf()
  ): T {
    return load(T::class.java, appName, environment, overrideFiles)
  }

  @JvmStatic
  fun <T : Config> load(
    configClass: Class<out Config>,
    appName: String,
    environment: Environment,
    overrideFiles: List<File> = listOf()
  ): T {
    val mapper = ObjectMapper(YAMLFactory()).registerModules(KotlinModule(), JavaTimeModule())
    val configYamls = loadConfigYamlMap(appName, environment, overrideFiles)

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

  @JvmStatic
  fun filesInDir(
    dir: String,
    filter: FilenameFilter = FilenameFilter { _, filename -> filename.endsWith(".yaml") }
  ): List<File> {
    val path = File(dir)

    check(path.exists() && path.isDirectory) { "$dir is not a valid directory" }
    return path.listFiles(filter).sorted()
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
    environment: Environment,
    overrideFiles: List<File>
  ): Map<String, String?> {
    // Load from jar files first, starting with the common config and then env specific config
    val embeddedConfigUrls = embeddedConfigFileNames(appName, environment)
        .map { it to Config::class.java.classLoader.getResource(it) }

    // Load from override files second, in the order specified, only if they exist
    val overrideFileUrls = overrideFiles
        .filter { it.exists() }
        .map { it.name to it.toURI().toURL() }

    // Produce a combined map of all of the results
    return (embeddedConfigUrls + overrideFileUrls).map { it.first to it.second?.readUtf8() }.toMap()
  }

  private fun URL.readUtf8(): String {
    return openStream().use {
      it.source().buffer().readUtf8()
    }
  }

  /** @return the list of config file names in the order they should be read */
  private fun embeddedConfigFileNames(appName: String, environment: Environment) =
      listOf("common", environment.name.toLowerCase()).map { "$appName-$it.yaml" }
}
