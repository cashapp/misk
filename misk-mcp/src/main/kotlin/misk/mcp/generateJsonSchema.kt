package misk.mcp

import kotlin.reflect.typeOf
import kotlinx.serialization.json.JsonObject
import misk.mcp.internal.generateJsonSchema

/**
 * Generates a JSON schema for a reified serializable Kotlin type.
 *
 * This is a convenience function that converts the reified type [T] to a [kotlin.reflect.KType] and delegates to
 * [kotlin.reflect.KType.generateJsonSchema] for schema generation.
 *
 * @param T the reified type to generate a schema for
 * @return JsonObject representing the generated JSON schema with type, properties, and required fields
 * @throws IllegalArgumentException if the class has no serializer
 * @see kotlin.reflect.KType.generateJsonSchema
 */
inline fun <reified T : Any> generateJsonSchema(): JsonObject = typeOf<T>().generateJsonSchema()
