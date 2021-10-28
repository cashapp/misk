package wisp.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Default build for Moshi using the Kotlin JSON adapter
 */
val DEFAULT_KOTLIN_MOSHI = buildMoshi(emptyList())

fun buildMoshi(jsonAdapters: List<Any>): Moshi {
  val builder = Moshi.Builder()

  jsonAdapters.forEach { jsonAdapter ->
    when (jsonAdapter) {
      is JsonAdapter.Factory -> builder.add(jsonAdapter)
      else -> builder.add(jsonAdapter)
    }
  }

  // Install last so that user adapters take precedence.
  builder.add(KotlinJsonAdapterFactory())

  return builder.build()
}
