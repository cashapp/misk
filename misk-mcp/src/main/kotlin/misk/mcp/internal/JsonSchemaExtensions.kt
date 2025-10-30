package misk.mcp.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import misk.mcp.Description
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Generates a JSON schema for a data class, including properties, types, and required fields.
 * Processes @Description annotations and handles nested objects with configurable recursion depth.
 * 
 * This function introspects the primary constructor parameters of a Kotlin data class and
 * generates a corresponding JSON schema that can be used for validation or documentation.
 * It handles nested objects, collections, maps, and primitive types.
 * 
 * @param level The recursion depth for nested object schema generation (default: 1)
 * @param description Optional description to include in the root schema object
 * @return JsonObject representing the generated JSON schema with type, properties, and required fields
 * @throws IllegalArgumentException if the class has no primary constructor
 */
@PublishedApi
internal fun <T : Any> KClass<T>.generateJsonSchema(level: Int = 1, description: String? = null): JsonObject {
  val ctor = primaryConstructor ?: throw IllegalArgumentException("No primary constructor")
  val properties = mutableMapOf<String, JsonObject>()
  val required = mutableListOf<String>()

  for (param in ctor.parameters) {
    val name = param.name ?: continue
    val description = param.findAnnotation<Description>()?.value
    properties[name] = param.type.generateJsonSchema(level, description)

    if (!param.isOptional && !param.type.isMarkedNullable) {
      required.add(name)
    }
  }

  return buildJsonObject {
    put("type", JsonPrimitive("object"))
    description?.let {
      put("description", JsonPrimitive(it))
    }
    put("properties", JsonObject(properties))
    if (level == 1) {
      put("required", JsonArray(required.map { JsonPrimitive(it) }))
    }
  }
}

/**
 * Generates JSON schema for a Kotlin type, handling primitives, collections, maps, enums, and nested objects.
 * 
 * @param level The current recursion depth for nested object processing
 * @param description Optional description to include in the schema
 * @return JsonObject representing the JSON schema for this type
 */
private fun KType.generateJsonSchema(level: Int, description: String? = null): JsonObject {
  return when (classifier) {
    Int::class, Long::class,
    Float::class, Double::class,
    String::class, Boolean::class -> buildJsonObject {
      put("type", JsonPrimitive(classifier.jsonType))
      description?.let {
        put("description", JsonPrimitive(it))
      }
    }

    List::class, Set::class -> buildJsonObject {
      put("type", JsonPrimitive(classifier.jsonType))
      description?.let {
        put("description", JsonPrimitive(it))
      }
      val collectionType = checkNotNull(arguments.firstOrNull()?.type) {
        "Collection type argument is required for $classifier"
      }
      put("items", collectionType.generateJsonSchema(level + 1))
    }

    Map::class -> buildJsonObject {
      put("type", JsonPrimitive(classifier.jsonType))
      description?.let {
        put("description", JsonPrimitive(it))
      }
      val collectionType = checkNotNull(arguments.getOrNull(1)?.type) {
        "Collection type argument is required for $classifier"
      }
      put("additionalProperties", collectionType.generateJsonSchema(level + 1))
    }

    else -> {
      val kClass = classifier as KClass<*>
      // Check if the class is an enum
      if (kClass.java.isEnum) {
        buildJsonObject {
          put("type", JsonPrimitive("string"))
          description?.let {
            put("description", JsonPrimitive(it))
          }
          // Get all enum constants and add them to the schema
          // Check for @SerialName annotation, fallback to enum constant name
          val enumValues = kClass.java.enumConstants.map { enumConstant ->
            val enumValue = enumConstant as Enum<*>
            // Get the field for this enum constant to check for @SerialName
            runCatching { kClass.java.getField(enumValue.name) }.getOrNull()
              ?.getAnnotation(SerialName::class.java)?.value
              ?: enumValue.name
          }
          put("enum", JsonArray(enumValues.map { JsonPrimitive(it) }))
        }
      } else {
        kClass.generateJsonSchema(level + 1, description)
      }
    }
  }
}

/**
 * Maps Kotlin types to their corresponding JSON schema type strings.
 * 
 * @return The JSON schema type string for the given Kotlin classifier
 * @throws IllegalArgumentException if the classifier is null
 */
private val KClassifier?.jsonType: String
  get() = when (this) {
    Int::class, Long::class -> "integer"
    Float::class, Double::class -> "number"
    String::class -> "string"
    Boolean::class -> "boolean"
    List::class, Set::class -> "array"
    Map::class -> "object"
    null -> throw IllegalArgumentException("KClassifier cannot be null when generating JSON schema")
    else -> "object"
  }
