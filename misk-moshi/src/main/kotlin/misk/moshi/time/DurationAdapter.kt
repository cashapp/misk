package misk.moshi.time

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Duration

/**
 * A Moshi JSON adapter for serializing and deserializing [Duration] objects.
 *
 * This adapter converts between JSON strings and Java [Duration] objects using the
 * ISO-8601 duration format (e.g., "PT1H30M" for 1 hour 30 minutes).
 *
 * **Serialization**: Converts [Duration] objects to their ISO-8601 string representation.
 * **Deserialization**: Parses ISO-8601 duration strings back into [Duration] objects.
 *
 * @see Duration.parse for supported ISO-8601 duration format
 * @see Duration.toString for the serialization format
 *
 * Example JSON representations:
 * - `"PT0S"` - zero duration
 * - `"PT1H"` - 1 hour
 * - `"PT5M"` - 5 minutes
 * - `"PT1H30M45S"` - 1 hour, 30 minutes, 45 seconds
 * - `"PT-10S"` - negative 10 seconds
 */
object DurationAdapter {
  @FromJson fun fromJson(duration: String?): Duration? {
    return duration?.let { Duration.parse(it) }
  }

  @ToJson fun toJson(value: Duration?): String? {
    return value?.toString()
  }
}
