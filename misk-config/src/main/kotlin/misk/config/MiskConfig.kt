package misk.config

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.ContextualSerializer
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinInvalidNullException
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.common.base.Joiner
import java.io.File
import java.io.FilenameFilter
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime
import misk.logging.getLogger
import misk.resources.ResourceLoader
import org.apache.commons.lang3.StringUtils
import wisp.deployment.Deployment

object MiskConfig {
  private val logger = getLogger<MiskConfig>()

  @JvmStatic
  inline fun <reified T : Config> load(
    appName: String,
    deployment: Deployment,
    overrideFiles: List<File> = listOf(),
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM,
  ): T {
    return load(T::class.java, appName, deployment, overrideFiles, resourceLoader)
  }

  @JvmStatic
  inline fun <reified T : Config> load(
    appName: String,
    deployment: Deployment,
    overrideResources: List<String> = listOf(),
    overrideValues: JsonNode? = null,
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM,
  ): T {
    return load(T::class.java, appName, deployment, overrideResources, overrideValues, resourceLoader)
  }

  @JvmStatic
  fun <T : Config> load(
    configClass: Class<out Config>,
    appName: String,
    deployment: Deployment,
    overrideFiles: List<File> = listOf(),
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM,
  ): T {
    val overrideFileUrls = overrideFiles.map { "filesystem:${it.absoluteFile}" }.filter { resourceLoader.exists(it) }
    return load(configClass, appName, deployment, overrideFileUrls, null, resourceLoader)
  }

  @JvmStatic
  fun <T : Config> load(
    configClass: Class<out Config>,
    appName: String,
    deployment: Deployment,
    overrideResources: List<String> = listOf(),
    overrideValues: JsonNode? = null,
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM,
  ): T {
    return load(
      configClass,
      appName,
      deployment,
      overrideResources,
      overrideValues,
      resourceLoader,
      failOnUnknownProperties = false,
    )
  }

  @JvmStatic
  fun <T : Config> load(
    configClass: Class<out Config>,
    appName: String,
    deployment: Deployment,
    overrideResources: List<String> = listOf(),
    overrideValues: JsonNode? = null,
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM,
    failOnUnknownProperties: Boolean,
  ): T {
    return load(
      configClass,
      appName,
      deployment,
      overrideResources,
      overrideValues,
      resourceLoader,
      failOnUnknownProperties,
      deserializerModifier = null,
    )
  }

  @JvmStatic
  fun <T : Config> load(
    configClass: Class<out Config>,
    appName: String,
    deployment: Deployment,
    overrideResources: List<String> = listOf(),
    overrideValues: JsonNode? = null,
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM,
    failOnUnknownProperties: Boolean,
    deserializerModifier: BeanDeserializerModifier? = null,
  ): T {
    check(!Secret::class.java.isAssignableFrom(configClass)) { "Top level service config cannot be a Secret<*>" }

    val mapper = newObjectMapper(resourceLoader, false, deserializerModifier)

    val configYamls = loadConfigYamlMap(appName, deployment, overrideResources, resourceLoader)
    check(configYamls.values.any { it != null }) { "could not find configuration files - checked ${configYamls.keys}" }

    val jsonNode = flattenYamlMap(configYamls, overrideValues)
    val configEnvironmentName = deployment.mapToEnvironmentName()

    val configFile = "$appName-${configEnvironmentName.lowercase(Locale.US)}.yaml"
    return readFlattenedYaml(
      mapper,
      jsonNode,
      configClass,
      configFile,
      appName,
      configEnvironmentName,
      failOnUnknownProperties,
    )
  }

  private fun <T : Config> readFlattenedYaml(
    mapper: ObjectMapper,
    jsonNode: JsonNode,
    configClass: Class<out Config>,
    configFile: String,
    appName: String,
    configEnvironmentName: String,
    failOnUnknownProperties: Boolean,
  ): T {
    try {
      @Suppress("UNCHECKED_CAST")
      return mapper.readValue(jsonNode.toString(), configClass) as T
    } catch (e: UnrecognizedPropertyException) {
      if (failOnUnknownProperties) {
        throw IllegalStateException("failed to load configuration for $appName $configEnvironmentName: ${e.message}", e)
      }

      val path = Joiner.on('.').join(e.path.map { it.fieldName ?: it.index })
      logger.warn(e) { "$configFile: '$path' not found in '${configClass.simpleName}', ignoring " + suggestSpelling(e) }

      // Try again, this time ignoring unknown properties.
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      return readFlattenedYaml(mapper, jsonNode, configClass, configFile, appName, configEnvironmentName, false)
    } catch (e: KotlinInvalidNullException) {
      throwMissingPropertyException(e, configClass, configFile, jsonNode)
    } catch (e: MismatchedInputException) {
      throwMissingPropertyException(e, configClass, configFile, jsonNode)
    } catch (e: Exception) {
      throw IllegalStateException("failed to load configuration for $appName $configEnvironmentName: ${e.message}", e)
    }
  }

  private fun throwMissingPropertyException(
    e: JsonMappingException,
    configClass: Class<out Config>,
    configFile: String,
    jsonNode: JsonNode,
  ): Nothing {
    val path = Joiner.on('.').join(e.path.map { it.fieldName ?: it.index })
    throw IllegalStateException(
      "could not find '${path}' of '${configClass.simpleName}'" +
        " in $configFile or in any of the combined logical config " +
        similarProperties(path, jsonNode),
      e,
    )
  }

  private fun allFieldNames(jsonNode: JsonNode, pathPrefix: String = ""): List<String> {
    if (jsonNode.isObject) {
      val objectNode = jsonNode as ObjectNode

      var seq = objectNode.fieldNames().asSequence().map { Joiner.on('.').join(pathPrefix, it) }

      // Recursively add the field names of any object fields.
      seq +=
        objectNode.fields().asSequence().flatMap {
          val nextPrefix = Joiner.on('.').join(pathPrefix, it.key)
          allFieldNames(it.value, nextPrefix)
        }

      return seq.toList()
    }
    return emptyList()
  }

  private fun suggestSpelling(e: UnrecognizedPropertyException): String {
    val suggestions = similarStrings(e.propertyName, e.knownPropertyIds.map { it.toString() })
    return if (suggestions.isNotEmpty()) "(Did you mean one of $suggestions?)" else ""
  }

  private fun similarProperties(fieldName: String, jsonNode: JsonNode): String {
    val suggestions = similarStrings(fieldName, allFieldNames(jsonNode))
    return if (suggestions.isNotEmpty()) "(Found similar: $suggestions)" else ""
  }

  private fun similarStrings(needle: String, haystack: List<String>) =
    haystack.filter {
      @Suppress("DEPRECATION")
      StringUtils.getLevenshteinDistance(it, needle) <= 2
    }

  fun <T : Config> toRedactedYaml(config: T, resourceLoader: ResourceLoader): String {
    val serializingMapper = newObjectMapper(resourceLoader, true, null)
    return serializingMapper.writeValueAsString(config)
  }

  private fun newObjectMapper(
    resourceLoader: ResourceLoader,
    redactSecrets: Boolean,
    deserializerModifier: BeanDeserializerModifier?,
  ): ObjectMapper {
    val mapper = ObjectMapper(YAMLFactory()).registerModules(KotlinModule.Builder().build(), JavaTimeModule())

    // Fail on null ints/doubles.
    mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)

    // The SecretDeserializer supports deserializing json, so bind last so it can use previous
    // mappings.
    if (redactSecrets) {
      mapper.registerModule(RedactSecretJacksonModule())
    } else {
      mapper.registerModule(SecretJacksonModule(resourceLoader, mapper))
    }

    // The ResourceAwareDeserializer lets string and other primitive types be loaded by reference using resource loader
    //   paths (classpath, filesystem, environment...) without using the Secret type.
    // This is useful for non-sensitive data or using environment variables to pass data into non-Secret types in
    //   existing config or framework provided config classes.
    mapper.registerModule(ResourceAwareJacksonModule(resourceLoader, mapper))

    // The deserializerModifier can be null if this mapper is serializing only.
    deserializerModifier?.let { mapper.registerModule(DeserializerModifierModule(it)) }

    return mapper
  }

  @JvmStatic
  fun filesInDir(
    dir: String,
    filter: FilenameFilter = FilenameFilter { _, filename -> filename.endsWith(".yaml") },
  ): List<File> {
    val path = File(dir)

    check(path.exists() && path.isDirectory) { "$dir is not a valid directory" }
    return path.listFiles(filter).sorted()
  }

  /**
   * Returns a JsonNode that combines the YAMLs in `configYamls`. If two nodes define the same value the last one wins.
   */
  private fun flattenYamlMap(configYamls: Map<String, String?>, overrideValues: JsonNode?): JsonNode {
    val mapper = ObjectMapper(YAMLFactory()).registerModules(KotlinModule.Builder().build(), JavaTimeModule())
    var result = mapper.createObjectNode()

    for ((key, value) in configYamls) {
      if (value == null) continue
      try {
        result = mapper.readerForUpdating(result).readValue(value)
      } catch (e: Exception) {
        throw IllegalStateException("could not parse $key: ${e.message}", e)
      }
    }
    if (overrideValues != null) {
      result = mapper.readerForUpdating(result).readValue(overrideValues)
    }
    return result
  }

  /**
   * Returns a map whose keys are the names of the source Yaml files to load, and whose values are the contents of those
   * files. If a file is absent the map’s value will be null.
   */
  fun loadConfigYamlMap(
    appName: String,
    deployment: Deployment,
    overrideResources: List<String>,
    resourceLoader: ResourceLoader = ResourceLoader.SYSTEM,
  ): Map<String, String?> {

    // Load from jar files first, starting with the common config and then env specific config
    val embeddedConfigUrls = embeddedConfigFileNames(appName, deployment).map { "classpath:/$it" }

    // Produce a combined map of all of the results
    return (embeddedConfigUrls + overrideResources).map { it to resourceLoader.utf8(it) }.toMap()
  }

  /** @return the list of config file names in the order they should be read */
  private fun embeddedConfigFileNames(appName: String, deployment: Deployment) =
    listOf("common", deployment.mapToEnvironmentName().lowercase(Locale.US)).map { "$appName-$it.yaml" }

  class SecretJacksonModule(val resourceLoader: ResourceLoader, val mapper: ObjectMapper) : SimpleModule() {
    override fun setupModule(context: SetupContext?) {
      addDeserializer(Secret::class.java, SecretDeserializer(resourceLoader, mapper))

      super.setupModule(context)
    }
  }

  class DeserializerModifierModule(val deserializerModifier: BeanDeserializerModifier) : SimpleModule() {
    override fun setupModule(context: SetupContext?) {
      setDeserializerModifier(deserializerModifier)
      super.setupModule(context)
    }
  }

  private class ResourceAwareJacksonModule(val resourceLoader: ResourceLoader, val mapper: ObjectMapper) :
    SimpleModule() {
    override fun setupModule(context: SetupContext?) {
      addDeserializer(String::class.java, ResourceAwareDeserializer<String>(resourceLoader, mapper))
      addDeserializer(Int::class.java, ResourceAwareDeserializer<Int>(resourceLoader, mapper))
      addDeserializer(Integer::class.java, ResourceAwareDeserializer<Integer>(resourceLoader, mapper))
      addDeserializer(Long::class.java, ResourceAwareDeserializer<Long>(resourceLoader, mapper))
      addDeserializer(java.lang.Long::class.java, ResourceAwareDeserializer<java.lang.Long>(resourceLoader, mapper))
      addDeserializer(Float::class.java, ResourceAwareDeserializer<Float>(resourceLoader, mapper))
      addDeserializer(java.lang.Float::class.java, ResourceAwareDeserializer<java.lang.Float>(resourceLoader, mapper))
      addDeserializer(Boolean::class.java, ResourceAwareDeserializer<Boolean>(resourceLoader, mapper))
      addDeserializer(
        java.lang.Boolean::class.java,
        ResourceAwareDeserializer<java.lang.Boolean>(resourceLoader, mapper),
      )

      super.setupModule(context)
    }
  }

  private inline fun <reified T : Any> ResourceAwareDeserializer(
    resourceLoader: ResourceLoader,
    mapper: ObjectMapper,
  ): ResourceAwareDeserializer<T> = ResourceAwareDeserializer(T::class, resourceLoader, mapper)

  private class ResourceAwareDeserializer<T : Any>(
    val typeClass: KClass<out T>,
    val resourceLoader: ResourceLoader,
    val mapper: ObjectMapper,
    val type: JavaType? = null,
  ) : JsonDeserializer<T>(), ContextualDeserializer {
    override fun deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext): T? {
      if (type == null) {
        // This only happens if ObjectMapper does not call createContextual for this property.
        throw JsonMappingException.from(jsonParser, "Attempting to deserialize an object with no type")
      }

      val valueAsType = jsonParser.valueAsTypeOrNull(type)
      val maybeReferenceWithMarkers = jsonParser.valueAsString

      // If the string starts with a known scheme, treat it as a resource reference.
      return resourceLoader.schemes
        .firstOrNull {
          // Only try to load as a resource if it uses YAML variable syntax like "${environment:MY_ENV_VAR}" or
          // "${filesystem:/path/to/file}".
          // Do not try to load if it is a reference without boundary markers since the caller does not want it inlined
          // & loaded.
          maybeReferenceWithMarkers.startsWith("\${$it")
        }
        ?.let { schema ->
          val content = maybeReferenceWithMarkers.removePrefix("\${").removeSuffix("}")

          // Parse the content more carefully to handle default values that contain colons (like URLs)
          // Expected formats: "scheme:path" or "scheme:path:-defaultValue"
          val firstColonIndex = content.indexOf(':')
          require(firstColonIndex > 0) {
            "Resource references for non-Secret fields must be in the form of \${scheme:path} or \${scheme:path:-defaultValue}"
          }

          val scheme = content.substring(0, firstColonIndex)
          val remainder = content.substring(firstColonIndex + 1)

          // Check if there's a default value (indicated by ":-")
          val defaultSeparator = ":-"
          val defaultIndex = remainder.indexOf(defaultSeparator)

          val (path, default) =
            if (defaultIndex >= 0) {
              // Has default value: "path:-defaultValue"
              val pathPart = remainder.substring(0, defaultIndex)
              val defaultPart = remainder.substring(defaultIndex + defaultSeparator.length)
              Pair(pathPart, defaultPart)
            } else {
              // No default value: just "path"
              Pair(remainder, null)
            }

          require(scheme.isNotEmpty() && path.isNotEmpty() && !path.startsWith(":")) {
            "Resource references for non-Secret fields must be in the form of \${scheme:path} or \${scheme:path:-defaultValue}"
          }

          val maybeReference = "$scheme:$path"

          resourceLoader.loadResource(maybeReference, type, mapper, default) as? T?
        } ?: valueAsType as? T? // Not a resource reference, return the type as is.
    }

    override fun createContextual(ctxt: DeserializationContext?, property: BeanProperty?): JsonDeserializer<*>? {
      return ResourceAwareDeserializer(typeClass, resourceLoader, mapper, mapper.constructType(typeClass.java))
    }
  }

  private class SecretDeserializer(
    val resourceLoader: ResourceLoader,
    val mapper: ObjectMapper,
    val type: JavaType? = null,
  ) : JsonDeserializer<Secret<*>>(), ContextualDeserializer {

    override fun createContextual(
      deserializationContext: DeserializationContext?,
      property: BeanProperty,
    ): JsonDeserializer<*> {
      return SecretDeserializer(resourceLoader, mapper, property.type.bindings.getBoundType(0))
    }

    override fun deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext): Secret<*>? {
      if (type == null) {
        // This only happens if ObjectMapper does not call createContextual for this property.
        throw JsonMappingException.from(jsonParser, "Attempting to deserialize an object with no type")
      }
      val reference = jsonParser.valueAsString
      return RealSecret(resourceLoader.loadResource(reference, type, mapper), reference)
    }
  }

  internal class RedactSecretJsonSerializer : JsonSerializer<Any>(), ContextualSerializer {
    override fun serialize(value: Any, gen: JsonGenerator, serializers: SerializerProvider) {
      gen.writeString("████████")
    }

    override fun createContextual(prov: SerializerProvider, property: BeanProperty): JsonSerializer<*> {
      return RedactSecretJsonSerializer()
    }
  }

  class RedactSecretJacksonModule : SimpleModule() {
    override fun setupModule(context: SetupContext?) {
      addSerializer(Secret::class.java, RedactSecretSerializer())
      super.setupModule(context)
    }
  }

  private class RedactSecretSerializer : JsonSerializer<Secret<*>>(), ContextualSerializer {
    override fun serialize(value: Secret<*>, gen: JsonGenerator, serializers: SerializerProvider?) {
      if ((value as? RealSecret<*>)?.reference?.isNotBlank() == true) {
        gen.writeString("${value.reference} -> ████████")
      } else {
        gen.writeString("████████")
      }
    }

    override fun createContextual(prov: SerializerProvider?, property: BeanProperty): JsonSerializer<*> {
      return RedactSecretSerializer()
    }
  }

  class RealSecret<T> @JvmOverloads constructor(override val value: T, internal val reference: String = "") :
    Secret<T> {
    override fun toString(): String = "RealSecret(value=████████, reference=$reference)"
  }

  private fun JsonParser.valueAsTypeOrNull(type: JavaType): Any? =
    when (type.rawClass) {
      String::class.java -> valueAsString
      Int::class.java,
      java.lang.Integer::class.java -> valueAsInt
      Double::class.java,
      java.lang.Double::class.java -> valueAsDouble
      Float::class.java,
      java.lang.Float::class.java -> valueAsDouble.toFloat()
      Long::class.java,
      java.lang.Long::class.java -> valueAsLong
      Byte::class.java,
      java.lang.Byte::class.java -> valueAsInt.toByte()
      Boolean::class.java,
      java.lang.Boolean::class.java -> valueAsBoolean
      else -> null
    }

  private fun String.toTypeOrNull(type: JavaType): Any? =
    when (type.rawClass) {
      String::class.java -> this
      Int::class.java -> this.toInt()
      java.lang.Integer::class.java -> this.toInt()
      Double::class.java -> this.toDouble()
      java.lang.Double::class.java -> this.toDouble()
      Float::class.java -> this.toFloat()
      java.lang.Float::class.java -> this.toFloat()
      Long::class.java -> this.toLong()
      java.lang.Long::class.java -> this.toLong()
      Byte::class.java -> this.toByte()
      java.lang.Byte::class.java -> this.toByte()
      ByteArray::class.java -> this.toByteArray()
      Boolean::class.java -> this.toBoolean()
      java.lang.Boolean::class.java -> this.toBoolean()
      else -> null
    }

  @OptIn(ExperimentalTime::class)
  private fun ResourceLoader.loadResource(
    reference: String,
    type: JavaType,
    mapper: ObjectMapper,
    default: String? = null,
  ): Any? {
    val resourceLoader = this
    val source = requireNotNull(resourceLoader.utf8(reference) ?: default) { "No resource found at: $reference." }

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
        return source.toTypeOrNull(type)
          ?: let {
            check(referenceFileExtension.isNotBlank()) {
              "Resource [$reference] needs a file extension for parsing ${type.rawClass}."
            }
            throw IllegalStateException("Unknown file extension \"$referenceFileExtension\" for resource [$reference].")
          }
      }
    }
  }
}

/**
 * Field or class will be redacted in dashboard output.
 *
 * ```
 * import misk.config.Redact
 *
 * data class MyServiceConfig(
 *   val customConfig: CustomConfig,
 *   val secretConfig: SecretConfig
 * )
 *
 * data class CustomConfig(
 *   @Redact
 *   val secretSubconfig: Subconfig
 * )
 *
 * @Redact
 * data class SecretConfig(
 *   val key: String
 * )
 * ```
 */
@JacksonAnnotationsInside
@JsonSerialize(using = MiskConfig.RedactSecretJsonSerializer::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class Redact
