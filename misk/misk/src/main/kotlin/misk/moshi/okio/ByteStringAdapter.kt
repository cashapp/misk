package misk.moshi.okio

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

/** JSON adapter converting [ByteString]s as base64 encoded strings */
object ByteStringAdapter {
  @ToJson fun toJson(value: ByteString) = value.base64Url()

  @FromJson fun fromJson(value: String) = value.decodeBase64()
      ?: throw JsonDataException("expected base64 but was $value")
}
