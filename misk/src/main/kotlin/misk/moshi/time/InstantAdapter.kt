package misk.moshi.time

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant
import java.util.Date

object InstantAdapter {
  @FromJson fun fromJson(date: Date?): Instant? {
    return date?.toInstant()
  }

  @ToJson fun toJson(value: Instant?): Date? {
    return when {
      value != null -> Date.from(value)
      else -> null
    }
  }
}
