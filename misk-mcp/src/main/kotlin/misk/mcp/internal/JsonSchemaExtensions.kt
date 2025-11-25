@file:OptIn( ExperimentalSerializationApi::class)

package misk.mcp.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import misk.mcp.Description
import misk.mcp.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Generates a JSON schema for a serializable Kotlin type, including properties, types, and required fields.
 * Processes @Description annotations and handles nested objects with configurable recursion depth.
 *
 * This function introspects the serial descriptor of the class to build a JSON schema representation.
 * It handles nested objects, collections, maps, and primitive types.
 *
 * @receiver the [KType] to generate a JSON schema for
 * @param description Optional description to include in the root schema object
 * @return JsonObject representing the generated JSON schema with type, properties, and required fields
 * @throws IllegalArgumentException if the class has no serializer
 */
@PublishedApi
internal fun KType.generateJsonSchema(description: String? = null): JsonObject =
  serializer().descriptor.generateJsonSchema(description)

/**
 * Generates a JSON schema for a reified serializable Kotlin type.
 *
 * This is a convenience function that converts the reified type [T] to a [KType] and delegates
 * to [KType.generateJsonSchema] for schema generation.
 *
 * @param T the reified type to generate a schema for
 * @param description Optional description to include in the root schema object
 * @return JsonObject representing the generated JSON schema with type, properties, and required fields
 * @throws IllegalArgumentException if the class has no serializer
 * @see KType.generateJsonSchema
 */
@PublishedApi
internal inline fun <reified T : Any> generateJsonSchema(description: String? = null): JsonObject =
  typeOf<T>().generateJsonSchema(description)

/**
 * Generates a JSON Schema representation for this [SerialDescriptor].
 *
 * Handles various Kotlin types including primitives, enums, lists, maps, and complex objects.
 * Supports recursive data structures by tracking visited descriptors to prevent infinite loops.
 * Includes support for the [Description] annotation on properties.
 *
 * @receiver the [SerialDescriptor] to generate a schema for
 * @param description optional description to include in the schema
 * @param accumulatedObjectDescriptors set of already visited descriptors to prevent infinite recursion
 * @return a [JsonObject] containing the JSON Schema representation
 */
private fun SerialDescriptor.generateJsonSchema(
  description: String? = null,
  accumulatedObjectDescriptors: Set<SerialDescriptor> = emptySet(),
): JsonObject = buildJsonObject {
  put("type", JsonPrimitive(kind.toJsonType()))
  description?.let {
    put("description", JsonPrimitive(it))
  }
  when (kind) {
    PrimitiveKind.BOOLEAN,
    PrimitiveKind.BYTE,
    PrimitiveKind.CHAR,
    PrimitiveKind.DOUBLE,
    PrimitiveKind.FLOAT,
    PrimitiveKind.INT,
    PrimitiveKind.LONG,
    PrimitiveKind.SHORT,
    PrimitiveKind.STRING -> {
    }

    SerialKind.ENUM -> {
      val enumValues = (0 until elementsCount).map { index ->
        getElementName(index)
      }
      put("enum", JsonArray(enumValues.map { JsonPrimitive(it) }))
    }

    StructureKind.LIST -> {
      val elementDescriptor = getElementDescriptor(0)
      val elementDescription = getElementAnnotations(0).description()
      put("items", elementDescriptor.generateJsonSchema(elementDescription, accumulatedObjectDescriptors))
    }

    StructureKind.MAP -> {
      description?.let {
        put("description", JsonPrimitive(it))
      }
      val valueDescriptor = getElementDescriptor(1)
      val valueDescription = getElementAnnotations(1).description()
      put("additionalProperties", valueDescriptor.generateJsonSchema(valueDescription, accumulatedObjectDescriptors.plus(this@generateJsonSchema)))
    }

    PolymorphicKind.OPEN,
    PolymorphicKind.SEALED,
    StructureKind.CLASS,
    StructureKind.OBJECT -> {
      if (this@generateJsonSchema in accumulatedObjectDescriptors) {
        // Prevent infinite recursion for recursive data structures
        return@buildJsonObject
      }
      val properties = mutableMapOf<String, JsonObject>()
      val required = mutableListOf<String>()

      elementDescriptors.forEachIndexed { index, paramDescriptor ->
        val name = getElementName(index)
        val description = getElementAnnotations(index).description()
        properties[name] = paramDescriptor.generateJsonSchema(description, accumulatedObjectDescriptors.plus(this@generateJsonSchema))

        if (!isElementOptional(index) && !paramDescriptor.isNullable) {
          required.add(name)
        }
      }

      put("properties", JsonObject(properties))
      put("required", JsonArray(required.map { JsonPrimitive(it) }))
    }

    SerialKind.CONTEXTUAL -> {}
  }
}

private fun List<Annotation>.description(): String? =
  filterIsInstance<Description>().firstOrNull()?.value

private fun SerialKind.toJsonType(): String = when (this) {
  PrimitiveKind.INT,
  PrimitiveKind.LONG,
  PrimitiveKind.BYTE,
  PrimitiveKind.SHORT -> "integer"

  PrimitiveKind.FLOAT,
  PrimitiveKind.DOUBLE -> "number"

  PrimitiveKind.BOOLEAN -> "boolean"

  PrimitiveKind.CHAR,
  PrimitiveKind.STRING,
  SerialKind.ENUM -> "string"

  StructureKind.LIST -> "array"

  PolymorphicKind.OPEN,
  PolymorphicKind.SEALED,
  SerialKind.CONTEXTUAL,
  StructureKind.CLASS,
  StructureKind.MAP,
  StructureKind.OBJECT -> "object"
}

