package misk.mcp.internal

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
 * Processes @Description annotations and handles nested objects.
 */
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
 * Generates JSON schema for a Kotlin type, handling primitives, collections, maps, and nested objects.
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

    else -> (classifier as KClass<*>).generateJsonSchema(level + 1, description)
  }
}

/**
 * Maps Kotlin types to their corresponding JSON schema type strings.
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
