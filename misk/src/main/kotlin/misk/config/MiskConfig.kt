package misk.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.google.common.base.Joiner
import misk.environment.Environment
import misk.logging.getLogger
import misk.resources.ResourceLoader
import okio.buffer
import okio.source
import org.apache.commons.lang3.StringUtils
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
    check(!Secret::class.java.isAssignableFrom(configClass)) {
      "Top level service config cannot be a Secret<*>"
    }

    val mapper = ObjectMapper(YAMLFactory()).registerModules(
        KotlinModule(),
        JavaTimeModule(),
        SecretJacksonModule())

    val configYamls = loadConfigYamlMap(appName, environment, overrideFiles)
    check(configYamls.values.any { it != null }) {
      "could not find configuration files - checked ${configYamls.keys}"
    }

    val jsonNode = flattenYamlMap(configYamls)

    val configFile = "$appName-${environment.name.toLowerCase()}.yaml"
    try {
      @Suppress("UNCHECKED_CAST")
      return mapper.readValue(jsonNode.toString(), configClass) as T
    } catch (e: MissingKotlinParameterException) {
      throw IllegalStateException(
          "could not find '${e.parameter.name}' of '${configClass.simpleName}'" +
              " in $configFile or in any of the combined logical config", e)
    } catch (e: UnrecognizedPropertyException) {
      val path = Joiner.on('.').join(e.path.map { it.fieldName })
      throw IllegalStateException(
          "error in $configFile: '$path' not found in '${configClass.simpleName}' ${suggestSpelling(e)}", e)
    } catch (e: Exception) {
      throw IllegalStateException(
          "failed to load configuration for $appName $environment: ${e.message}", e)
    }
  }

  private fun suggestSpelling(e: UnrecognizedPropertyException): String {
    val suggestions = e.knownPropertyIds.filter {
      @Suppress("DEPRECATION")
      StringUtils.getLevenshteinDistance(it.toString(), e.propertyName) <= 2
    }
    return if (suggestions.isNotEmpty()) "(Did you mean one of $suggestions?)" else ""
  }

  fun <T : Config> toYaml(config: T): String {
    val mapper = ObjectMapper(YAMLFactory()).registerModules(
        KotlinModule(),
        JavaTimeModule(),
        SecretJacksonModule())
    return mapper.writeValueAsString(config)
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
        .map { it.toURI().toURL().toString() to it.toURI().toURL() }

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

  class SecretJacksonModule() : SimpleModule() {
    override fun setupModule(context: SetupContext?) {
      addDeserializer(Secret::class.java, SecretDeserializer())
      super.setupModule(context)
    }
  }

  private class SecretDeserializer(val type: JavaType? = null) : JsonDeserializer<Secret<*>>(),
      ContextualDeserializer {

    override fun createContextual(
      deserializationContext: DeserializationContext?,
      property: BeanProperty
    ): JsonDeserializer<*> {
      return SecretDeserializer(property.type.bindings.getBoundType(0))
    }

    override fun deserialize(
      jsonParser: JsonParser,
      deserializationContext: DeserializationContext
    ): Secret<*>? {
      if (type == null) {
        // This only happens if ObjectMapper does not call createContextual fo this property.
        throw JsonMappingException.from(jsonParser,
            "Attempting to deserialize an object with no type")
      }
      val reference = deserializationContext.readValue(jsonParser, String::class.java) as String
      return RealSecret(loadSecret(reference, type))
    }

    private fun loadSecret(reference: String, type: JavaType): Any {
      val source = requireNotNull(ResourceLoader.SYSTEM.utf8(reference)) {
        "No secret found at: $reference."
      }
      val referenceFileExtension = Regex(".*\\.([^.]+)$").find(reference)?.groupValues?.get(1) ?: ""
      return when (referenceFileExtension) {
        "yaml" -> {
          mapper.readValue(source, type) as Any
        }
        "txt" -> {
          check(type.rawClass.isAssignableFrom(String::class.java)) {
            "Secrets with the .txt extension map to Secret<String> fields in Config classes."
          }
          source
        }
        else -> {
          check(referenceFileExtension.isNotBlank()) {
            "Secret [$reference] needs a file extension for parsing."
          }
          throw IllegalStateException(
              "Unknown file extension \"$referenceFileExtension\" for secret [$reference].")
        }
      }
    }

    companion object {
      private val mapper =
          ObjectMapper(YAMLFactory()).registerModules(KotlinModule(), SecretJacksonModule())
    }
  }

  class RealSecret<T>(override val value: T) : Secret<T>
}
