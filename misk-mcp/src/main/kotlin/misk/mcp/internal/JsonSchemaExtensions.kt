@file:OptIn(ExperimentalSerializationApi::class)

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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import misk.mcp.Description
import misk.mcp.serializer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Generates a JSON schema for a reified serializable Kotlin type.
 *
 * This is a convenience function that converts the reified type [T] to a [KType] and delegates
 * to [KType.generateJsonSchema] for schema generation.
 *
 * @param T the reified type to generate a schema for
 * @return JsonObject representing the generated JSON schema with type, properties, and required fields
 * @throws IllegalArgumentException if the class has no serializer
 * @see KType.generateJsonSchema
 */
@PublishedApi
internal inline fun <reified T : Any> generateJsonSchema(): JsonObject =
  typeOf<T>().generateJsonSchema()

/**
 * Generates a JSON schema for a serializable Kotlin type, including properties, types, and required fields.
 * Processes @Description annotations and handles nested objects with configurable recursion depth.
 *
 * This function introspects the serial descriptor of the class to build a JSON schema representation.
 * It handles nested objects, collections, maps, and primitive types.
 *
 * @receiver the [KType] to generate a JSON schema for
 * @return JsonObject representing the generated JSON schema with type, properties, and required fields
 * @throws IllegalArgumentException if the class has no serializer
 */
@PublishedApi
internal fun KType.generateJsonSchema(): JsonObject =
  with(serializer().descriptor){
    generateJsonSchema(annotations.description())
  }

/**
 * Generates a JSON Schema representation for this [SerialDescriptor].
 *
 * Handles various Kotlin types including primitives, enums, lists, maps, and complex objects.
 * Supports recursive data structures by tracking visited descriptors to prevent infinite loops.
 * Includes support for the [Description] annotation on properties.
 *
 * @receiver the [SerialDescriptor] to generate a schema for
 * @param description optional description to include in the schema
 * @return a [JsonObject] containing the JSON Schema representation
 */
private fun SerialDescriptor.generateJsonSchema(description: String? = null): JsonObject {
  val (schema, requiredDefinitions) = generateJsonSchemaInternal(description)
  val schemaWithDefinitions = buildJsonObject {
    schema.forEach { put(it.key, it.value) }
    val requiredDefinitionsWithDependencies = requiredDefinitions.flatMap {
      JSON_SCHEMA_REF_TO_DEPENDENT_REFS[it]?.plus(it) ?: listOf(it)
    }.toSet()
    put("\$defs", buildJsonObject {
      requiredDefinitionsWithDependencies.forEach { definition ->
        put(definition, JSON_SCHEMA_REF_TO_DEFINITION[definition]!!)
      }
    })
  }
  return schemaWithDefinitions
}

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
 * @return a [SchemaWithDefinitions] that contains a [JsonObject] containing the JSON Schema representation, plus
 *         the names of any required definitions that should be included.
 */
private fun SerialDescriptor.generateJsonSchemaInternal(
  description: String? = null,
  accumulatedObjectDescriptors: Set<SerialDescriptor> = emptySet(),
  label: String? = null,
): SchemaWithDefinitions {
  // Handle specific types with custom schemas.
  if (serialName in SERIAL_NAME_TO_JSON_SCHEMA_REF.keys) {
    val reference = SERIAL_NAME_TO_JSON_SCHEMA_REF[serialName]!!
    return SchemaWithDefinitions(
      schema = buildJsonObject {
        put("\$ref", JsonPrimitive("$JSON_REF_PREFIX$reference"))
      },
      requiredDefinitions = setOf(reference),
    )
  }
  val referencedDefinitions = mutableSetOf<String>()
  val schema = buildJsonObject {
    description?.let {
      put("description", JsonPrimitive(it))
    }
    kind.toJsonType()?.let {
      put("type", JsonPrimitive(it))
    }
    if (this@generateJsonSchemaInternal in accumulatedObjectDescriptors) {
      // Prevent infinite recursion for recursive data structures
      return@buildJsonObject
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
        label?.let {
          put("properties", buildLabelProperty(it))
        }
      }

      SerialKind.ENUM -> {
        val enumValues = (0 until elementsCount).map { index ->
          getElementName(index)
        }
        label?.let {
          put("properties", buildLabelProperty(it))
        }
        put("enum", JsonArray(enumValues.map { JsonPrimitive(it) }))
      }

      StructureKind.LIST -> {
        val elementDescriptor = getElementDescriptor(0)
        val elementDescription = getElementAnnotations(0).description()
        label?.let {
          put("properties", buildLabelProperty(it))
        }
        val (subschema, subdefinitions) = elementDescriptor.generateJsonSchemaInternal(elementDescription, accumulatedObjectDescriptors)
        put("items", subschema)
        referencedDefinitions.addAll(subdefinitions)
      }

      StructureKind.MAP -> {
        description?.let {
          put("description", JsonPrimitive(it))
        }
        label?.let {
          put("properties", buildLabelProperty(it))
        }
        val valueDescriptor = getElementDescriptor(1)
        val valueDescription = getElementAnnotations(1).description()
        val (subschema, subdefinitions) =
          valueDescriptor.generateJsonSchemaInternal(valueDescription, accumulatedObjectDescriptors.plus(this@generateJsonSchemaInternal))
        put("additionalProperties", subschema)
        referencedDefinitions.addAll(subdefinitions)
      }

      StructureKind.CLASS,
      StructureKind.OBJECT -> {
        val properties = mutableMapOf<String, JsonObject>()
        val required = mutableListOf<String>()

        label?.let {
          properties["type"] = buildLabelProperty(it)["type"] as JsonObject
        }

        elementDescriptors.forEachIndexed { index, paramDescriptor ->
          val name = getElementName(index)
          val description = getElementAnnotations(index).description()
          val (subschema, subdefinitions) =
            paramDescriptor.generateJsonSchemaInternal(description, accumulatedObjectDescriptors.plus(this@generateJsonSchemaInternal))
          properties[name] = subschema
          referencedDefinitions.addAll(subdefinitions)

          if (!isElementOptional(index) && !paramDescriptor.isNullable) {
            required.add(name)
          }
        }

        put("properties", JsonObject(properties))
        put("required", JsonArray(required.map { JsonPrimitive(it) }))
      }

      PolymorphicKind.OPEN,
      PolymorphicKind.SEALED -> {
        put("oneOf", buildJsonArray {
          // Auto-generated sealed descriptors have a "type" and "value" field, where "value" contains the actual subtype descriptors.
          // For custom serialized sealed descriptors, the subtype descriptors are directly under the sealed descriptor.
          if (elementsCount > 1 && getElementName(1) == "value" && getElementDescriptor(1).kind == SerialKind.CONTEXTUAL) {
            getElementDescriptor(1).elementDescriptors
          } else {
            elementDescriptors
          }.forEach { paramDescriptor ->
            val (subschema, subdefinitions) = paramDescriptor.generateJsonSchemaInternal(
              description = paramDescriptor.annotations.description(),
              accumulatedObjectDescriptors = accumulatedObjectDescriptors.plus(this@generateJsonSchemaInternal),
              label = paramDescriptor.serialName,
            )
            add(subschema)
            referencedDefinitions.addAll(subdefinitions)
          }
        })
      }

      SerialKind.CONTEXTUAL -> {}
    }
  }
  return SchemaWithDefinitions(schema, referencedDefinitions)
}

data class SchemaWithDefinitions(
  val schema: JsonObject,
  val requiredDefinitions: Set<String>,
)

private fun List<Annotation>.description(): String? =
  filterIsInstance<Description>().firstOrNull()?.value

private fun buildLabelProperty(label: String) = buildJsonObject {
  put("type", buildJsonObject {
    put("const", JsonPrimitive(label))
  })
}

private fun SerialKind.toJsonType(): String? = when (this) {
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
  SerialKind.CONTEXTUAL,
  StructureKind.CLASS,
  StructureKind.MAP,
  StructureKind.OBJECT -> "object"

  // Sealed interfaces and classes are represented using oneOf, so they could have multiple types.
  PolymorphicKind.SEALED -> null
}

