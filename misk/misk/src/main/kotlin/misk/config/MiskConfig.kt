package misk.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import misk.environment.Environment
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

object MiskConfig {
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

    var jsonNode: JsonNode? = null
    val missingConfigFiles = mutableListOf<String>()
    for (configFileName in configFileNames(appName, environment)) {
      val url = getResource(configClass, configFileName)
      if (url == null) {
        missingConfigFiles.add(configFileName)
        continue
      }

      try {
        open(url).use {
          val objectReader = if (jsonNode == null) {
            mapper.readerFor(JsonNode::class.java)
          } else {
            mapper.readerForUpdating(jsonNode)
          }
          jsonNode = objectReader.readValue(it)
        }
      } catch (e: Exception) {
        throw IllegalStateException("could not parse $configFileName: ${e.message}", e)
      }
    }

    if (jsonNode == null) {
      val configFileMessage = missingConfigFiles.joinToString(", ")
      throw IllegalStateException(
          "could not find configuration files - checked [$configFileMessage]"
      )
    }

    @Suppress("UNCHECKED_CAST")
    try {
      return mapper.readValue(jsonNode.toString(), configClass) as T
    } catch (e: MissingKotlinParameterException) {
      throw IllegalStateException("could not find configuration for ${e.parameter.name}", e)
    } catch (e: Exception) {
      throw IllegalStateException(e)
    }
  }

  /** @return the list of config file names in the order they should be read */
  private fun configFileNames(appName: String, environment: Environment): List<String> =
      listOf("common", environment.name.toLowerCase()).map { "$appName-$it.yaml" }

  private fun open(url: URL): BufferedReader {
    return BufferedReader(InputStreamReader(url.openStream()))
  }

  private fun getResource(
    configClass: Class<out Config>,
    fileName: String
  ) = configClass.classLoader.getResource(fileName)
}
