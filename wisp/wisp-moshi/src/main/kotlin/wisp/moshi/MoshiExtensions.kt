package wisp.moshi

import com.squareup.moshi.Moshi

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(expression = "adapter()", imports = ["misk.moshi.adapter"]),
)
inline fun <reified T> Moshi.adapter() = this.adapter(T::class.java)!!
