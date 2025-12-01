package misk.mcp.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal const val JSON_REF_PREFIX = "#/\$defs/"

internal const val JSON_PRIMITIVE_REF = "JsonPrimitive"
internal const val JSON_ARRAY_REF = "JsonArray"
internal const val JSON_OBJECT_REF = "JsonObject"
internal const val JSON_ELEMENT_REF = "JsonElement"

/**
 * Kotlinx serializer serial names to JSON Schema $ref identifiers.
 */
internal val SERIAL_NAME_TO_JSON_SCHEMA_REF = mapOf(
  JsonPrimitive.serializer().descriptor.serialName to JSON_PRIMITIVE_REF,
  JsonArray.serializer().descriptor.serialName to JSON_ARRAY_REF,
  JsonObject.serializer().descriptor.serialName to JSON_OBJECT_REF,
  JsonElement.serializer().descriptor.serialName to JSON_ELEMENT_REF,
)

/**
 * JSON Schema $ref identifiers to their dependent $ref identifiers.
 *
 * This mapping indicates which other definitions are required when a particular definition is referenced.
 */
internal val JSON_SCHEMA_REF_TO_DEPENDENT_REFS = mapOf(
  JSON_PRIMITIVE_REF to emptyList(),
  JSON_ELEMENT_REF to listOf(JSON_PRIMITIVE_REF, JSON_ARRAY_REF, JSON_OBJECT_REF),
  JSON_OBJECT_REF to listOf(JSON_PRIMITIVE_REF, JSON_ARRAY_REF, JSON_ELEMENT_REF),
  JSON_ARRAY_REF to listOf(JSON_PRIMITIVE_REF, JSON_OBJECT_REF, JSON_ELEMENT_REF),
)

internal val JSON_SCHEMA_REF_TO_DEFINITION = mapOf(
  JSON_PRIMITIVE_REF to buildJsonObject {
    put("oneOf", buildJsonArray {
      add(buildJsonObject { put("type", JsonPrimitive("string")) })
      add(buildJsonObject { put("type", JsonPrimitive("number")) })
      add(buildJsonObject { put("type", JsonPrimitive("boolean")) })
      add(buildJsonObject { put("const", JsonNull) })
    })
  },
  JSON_ELEMENT_REF to buildJsonObject {
    put("oneOf", buildJsonArray {
      add(buildJsonObject { put("\$ref", JsonPrimitive(JSON_REF_PREFIX + JSON_PRIMITIVE_REF)) })
      add(buildJsonObject { put("\$ref", JsonPrimitive(JSON_REF_PREFIX + JSON_ARRAY_REF)) })
      add(buildJsonObject { put("\$ref", JsonPrimitive(JSON_REF_PREFIX + JSON_OBJECT_REF)) })
    })
  },
  JSON_ARRAY_REF to buildJsonObject {
    put("type", JsonPrimitive("array"))
    put("items", buildJsonObject {
      put("\$ref", JsonPrimitive(JSON_REF_PREFIX + JSON_ELEMENT_REF))
    })
  },
  JSON_OBJECT_REF to buildJsonObject {
    put("type", JsonPrimitive("object"))
    put("additionalProperties", buildJsonObject {
      put("\$ref", JsonPrimitive(JSON_REF_PREFIX + JSON_ELEMENT_REF))
    })
  },
)
