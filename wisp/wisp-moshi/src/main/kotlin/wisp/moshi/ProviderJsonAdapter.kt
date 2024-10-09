package wisp.moshi

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import com.google.inject.Provider as GuiceProvider
import javax.inject.Provider as JavaxProvider
import jakarta.inject.Provider as JakartaProvider

class GuiceProviderJsonAdapter<T>(private val delegate: JsonAdapter<T>) : JsonAdapter<GuiceProvider<T>>() {
  @FromJson
  override fun fromJson(reader: JsonReader): GuiceProvider<T> {
    val value = delegate.fromJson(reader)
    return GuiceProvider { value }
  }

  @ToJson
  override fun toJson(writer: JsonWriter, value: GuiceProvider<T>?) {
    delegate.toJson(writer, value?.get())
  }
}

class JavaxProviderJsonAdapter<T>(private val delegate: JsonAdapter<T>) : JsonAdapter<JavaxProvider<T>>() {
  @FromJson
  override fun fromJson(reader: JsonReader): JavaxProvider<T> {
    val value = delegate.fromJson(reader)
    return JavaxProvider { value }
  }

  @ToJson
  override fun toJson(writer: JsonWriter, value: JavaxProvider<T>?) {
    delegate.toJson(writer, value?.get())
  }
}

class JakartaProviderJsonAdapter<T>(private val delegate: JsonAdapter<T>) : JsonAdapter<JakartaProvider<T>>() {
  @FromJson
  override fun fromJson(reader: JsonReader): JakartaProvider<T> {
    val value = delegate.fromJson(reader)
    return JakartaProvider { value }
  }

  @ToJson
  override fun toJson(writer: JsonWriter, value: JakartaProvider<T>?) {
    delegate.toJson(writer, value?.get())
  }
}


class ProviderJsonAdapterFactory : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    // Step 1: Check the raw type is what you want to match against
    val rawType = Types.getRawType(type)
    if (rawType != GuiceProvider::class.java && rawType != JavaxProvider::class.java && rawType != JakartaProvider::class.java) {
      return null
    }

    // Step 2: Check the type is actually parameterized
    if (type !is ParameterizedType) {
      return null
    }

    // Step 3: Extract the parameterized type's upper bound
    val providerType = getParameterUpperBound(0, type)

    // Step 4: Look up the adapter for the parameterized type
    val delegate = moshi.adapter<Any>(providerType, annotations)

    // Step 5: Wrap its adapter in the respective provider adapter
    return when (rawType) {
      GuiceProvider::class.java -> GuiceProviderJsonAdapter(delegate)
      JavaxProvider::class.java -> JavaxProviderJsonAdapter(delegate)
      JakartaProvider::class.java -> JakartaProviderJsonAdapter(delegate)
      else -> null
    }
  }

  private fun getParameterUpperBound(index: Int, type: ParameterizedType): Type {
    val types = type.actualTypeArguments
    require(!(index < 0 || index >= types.size)) { "Index " + index + " not in range [0," + types.size + ") for " + type }
    val paramType = types[index]
    if (paramType is WildcardType) {
      return paramType.upperBounds[0]
    }
    return paramType
  }
}
