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
import javax.inject.Inject
import javax.inject.Provider

internal class ConfigProvider<T : Config>(
    private val configClass: Class<out Config>,
    private val appName: String
) : Provider<T> {
  @Inject
  lateinit var environment: Environment

  override fun get(): T {
    val mapper = ObjectMapper(YAMLFactory())
    mapper.registerModule(KotlinModule())
    mapper.registerModule(JavaTimeModule())

    var jsonNode: JsonNode? = null
    val missingConfigFiles = mutableListOf<String>()
    for (configFileName in configFileNames) {
      val url = getResource(configFileName)
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
  private val configFileNames
    get() = listOf("common", environment.name.toLowerCase()).map { "$appName-$it.yaml" }

  private fun open(url: URL): BufferedReader {
    return BufferedReader(InputStreamReader(url.openStream()))
  }

  private fun getResource(fileName: String) = configClass.classLoader.getResource(fileName)
}

