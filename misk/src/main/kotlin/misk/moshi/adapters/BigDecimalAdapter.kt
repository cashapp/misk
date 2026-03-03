package misk.moshi.adapters

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.math.BigDecimal

object BigDecimalAdapter {
  @FromJson
  fun decode(decimal: String): BigDecimal = BigDecimal(decimal)

  @ToJson
  fun encode(decimal: BigDecimal): String = decimal.toString()
}
