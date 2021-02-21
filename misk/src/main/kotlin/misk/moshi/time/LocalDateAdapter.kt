package misk.moshi.time

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

object LocalDateAdapter {
  @FromJson fun fromJson(date: Date?): LocalDate? {
    return LocalDate.ofInstant(date?.toInstant(), ZoneId.systemDefault())
  }

  @ToJson fun toJson(value: LocalDate?): Date? {
    return when {
      value != null -> Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant())
      else -> null
    }
  }
}
