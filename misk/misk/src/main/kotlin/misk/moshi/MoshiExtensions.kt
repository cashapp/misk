package misk.moshi

import com.squareup.moshi.Moshi

inline fun <reified T> Moshi.adapter() = adapter(T::class.java)
