package misk.moshi.time

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.format.DateTimeFormatter

object OffsetDateTimeAdapter {
  @ToJson
  fun toJson(value: java.time.OffsetDateTime?): String? {
    return when {
      value != null -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value)
      else -> null
    }
  }

  @FromJson
  fun fromJson(value: String?): java.time.OffsetDateTime? {
    return when {
      value != null -> java.time.OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
      else -> null
    }
  }
}
