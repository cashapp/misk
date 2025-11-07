package misk.moshi.time

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Duration

object DurationAdapter {
  @FromJson fun fromJson(duration: String?): Duration? {
    return duration?.let { Duration.parse(it) }
  }

  @ToJson fun toJson(value: Duration?): String? {
    return value?.toString()
  }
}