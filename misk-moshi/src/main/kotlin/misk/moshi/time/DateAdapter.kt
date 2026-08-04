package misk.moshi.time

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import java.lang.reflect.Type
import java.util.Date

object DateAdapter : JsonAdapter.Factory {
  private val delegate = Rfc3339DateJsonAdapter()

  override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
    return if (Types.equals(type, Date::class.java)) delegate else null
  }
}
