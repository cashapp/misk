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
import misk.resources.ResourceLoader
import org.apache.commons.lang3.StringUtils
import wisp.deployment.Deployment
import java.io.File
import java.io.FilenameFilter
import java.util.Locale

object MiskConfig {
  @JvmStatic
  inline fun <reified T : wisp.config.Config> load(
    appName: String,
    deployment: Deployment,
    overrideFiles: List<File> = listOf(),
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM
  ): T {
    return load(T::class.java, appName, deployment, overrideFiles, resourceLoader)
  }

  @JvmStatic
  fun <T : wisp.config.Config> load(
    configClass: Class<out wisp.config.Config>,
    appName: String,
    deployment: Deployment,
    overrideFiles: List<File> = listOf(),
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM
  ): T {
    check(!Secret::class.java.isAssignableFrom(configClass)) {
      "Top level service config cannot be a Secret<*>"
    }

    val mapper = newObjectMapper(resourceLoader)

    val configYamls = loadConfigYamlMap(appName, deployment, overrideFiles, resourceLoader)
    check(configYamls.values.any { it != null }) {
      "could not find configuration files - checked ${configYamls.keys}"
    }

    val jsonNode = flattenYamlMap(configYamls)
    val configEnvironmentName = deployment.mapToEnvironmentName()

    val configFile = "$appName-${configEnvironmentName.toLowerCase(Locale.US)}.yaml"
    try {
      @Suppress("UNCHECKED_CAST")
      return mapper.readValue(jsonNode.toString(), configClass) as T
    } catch (e: MissingKotlinParameterException) {
      throw IllegalStateException(
        "could not find '${e.parameter.name}' of '${configClass.simpleName}'" +
          " in $configFile or in any of the combined logical config",
        e
      )
    } catch (e: UnrecognizedPropertyException) {
      val path = Joiner.on('.').join(e.path.map { it.fieldName })
      throw IllegalStateException(
        "error in $configFile: '$path' not found in '${configClass.simpleName}' ${
          suggestSpelling(
            e
          )
        }",
        e
      )
    } catch (e: Exception) {
      throw IllegalStateException(
        "failed to load configuration for $appName $configEnvironmentName: ${e.message}", e
      )
    }
  }

  private fun suggestSpelling(e: UnrecognizedPropertyException): String {
    val suggestions = e.knownPropertyIds.filter {
      @Suppress("DEPRECATION")
      StringUtils.getLevenshteinDistance(it.toString(), e.propertyName) <= 2
    }
    return if (suggestions.isNotEmpty()) "(Did you mean one of $suggestions?)" else ""
  }

  fun <T : wisp.config.Config> toYaml(config: T, resourceLoader: ResourceLoader): String {
    return newObjectMapper(resourceLoader).writeValueAsString(config)
  }

  private fun newObjectMapper(resourceLoader: ResourceLoader): ObjectMapper {
    val mapper = ObjectMapper(YAMLFactory()).registerModules(
      KotlinModule(),
      JavaTimeModule()
    )

    // The SecretDeserializer supports deserializing json, so bind last so it can use previous
    // mappings.
    mapper.registerModule(SecretJacksonModule(resourceLoader, mapper))
    return mapper
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
    deployment: Deployment,
    overrideFiles: List<File>,
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM
  ): Map<String, String?> {

    // Load from jar files first, starting with the common config and then env specific config
    val embeddedConfigUrls = embeddedConfigFileNames(appName, deployment)
      .map { "classpath:/$it" }

    // Load from override files second, in the order specified, only if they exist
    val overrideFileUrls = overrideFiles.map { "filesystem:${it.absoluteFile}" }
      .filter { resourceLoader.exists(it) }

    // Produce a combined map of all of the results
    return (embeddedConfigUrls + overrideFileUrls)
      .map { it to resourceLoader.utf8(it) }
      .toMap()
  }

  /** @return the list of config file names in the order they should be read */
  private fun embeddedConfigFileNames(appName: String, deployment: Deployment) =
    listOf(
      "common",
      deployment.mapToEnvironmentName().toLowerCase(Locale.US)
    ).map { "$appName-$it.yaml" }

  class SecretJacksonModule(val resourceLoader: ResourceLoader, val mapper: ObjectMapper) :
    SimpleModule() {
    override fun setupModule(context: SetupContext?) {
      addDeserializer(Secret::class.java, SecretDeserializer(resourceLoader, mapper))
      super.setupModule(context)
    }
  }

  private class SecretDeserializer(
    val resourceLoader: ResourceLoader,
    val mapper: ObjectMapper,
    val type: JavaType? = null
  ) : JsonDeserializer<Secret<*>>(),
    ContextualDeserializer {

    override fun createContextual(
      deserializationContext: DeserializationContext?,
      property: BeanProperty
    ): JsonDeserializer<*> {
      return SecretDeserializer(resourceLoader, mapper, property.type.bindings.getBoundType(0))
    }

    override fun deserialize(
      jsonParser: JsonParser,
      deserializationContext: DeserializationContext
    ): Secret<*>? {
      if (type == null) {
        // This only happens if ObjectMapper does not call createContextual fo this property.
        throw JsonMappingException.from(
          jsonParser,
          "Attempting to deserialize an object with no type"
        )
      }
      val reference = deserializationContext.readValue(jsonParser, String::class.java) as String
      return RealSecret(loadSecret(reference, type))
    }

    private fun loadSecret(reference: String, type: JavaType): Any {
      val source = requireNotNull(resourceLoader.utf8(reference)) {
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
          // Ignore extension if we're requesting a string or a bytearray
          if (type.rawClass == String::class.java) {
            return source
          } else if (type.isArrayType && type.contentType.rawClass == Byte::class.java) {
            return source.toByteArray()
          }

          check(referenceFileExtension.isNotBlank()) {
            "Secret [$reference] needs a file extension for parsing."
          }
          throw IllegalStateException(
            "Unknown file extension \"$referenceFileExtension\" for secret [$reference]."
          )
        }
      }
    }
  }

  class RealSecret<T>(override val value: T) : Secret<T>
}
