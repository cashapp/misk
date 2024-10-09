package wisp.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Default build for Moshi using the Kotlin JSON adapter
 */
val defaultKotlinMoshi = buildMoshi(listOf(ProviderJsonAdapterFactory()))

@JvmOverloads
fun buildMoshi(
  jsonAdapters: List<Any>,
  jsonLastAdapters: List<Any> = emptyList()
): Moshi {
  val builder = Moshi.Builder()

  jsonAdapters.forEach { jsonAdapter ->
    // There are multiple overloads of add() that do different things based on the type of the param.
    // This forces the correct overload for be called for JsonAdapter.Factory
    when (jsonAdapter) {
      is JsonAdapter.Factory -> builder.add(jsonAdapter)
      else -> builder.add(jsonAdapter)
    }
  }

  // addLast() so that user adapters take precedence
  jsonLastAdapters.forEach { jsonAdapter ->
    // There are multiple overloads of add() that do different things based on the type of the param.
    // This forces the correct overload for be called for JsonAdapter.Factory
    when (jsonAdapter) {
      is JsonAdapter.Factory -> builder.addLast(jsonAdapter)
      else -> builder.addLast(jsonAdapter)
    }
  }
  
  builder.addLast(KotlinJsonAdapterFactory())

  return builder.build()
}
