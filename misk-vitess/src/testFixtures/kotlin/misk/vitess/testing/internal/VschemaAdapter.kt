package misk.vitess.testing.internal

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * A simple adapter to convert a Vschema JSON string to a Map<String, Any>, along with some helper methods to convert
 * values to Map types. This is sufficient for the purposes of simple parsing and linting. We should consider creating a
 * Vschema data class if more robust parsing is needed.
 */
class VschemaAdapter {
  private val mapAdapter: JsonAdapter<Map<String, Any>> =
    Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
      .adapter(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

  fun fromJson(json: String): Map<String, Any> = mapAdapter.fromJson(json) ?: emptyMap()

  fun toMap(value: Any?): Map<String, Any> = value as? Map<String, Any> ?: emptyMap()

  fun toListMap(value: Any?): List<Map<String, Any>> = value as? List<Map<String, Any>> ?: emptyList()
}
