package wisp.moshi

import com.squareup.moshi.Moshi

inline fun <reified T> Moshi.adapter() = this.adapter(T::class.java)!!
