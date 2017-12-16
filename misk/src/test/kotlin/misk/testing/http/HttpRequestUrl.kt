package misk.testing.http

import com.google.common.base.Joiner
import okhttp3.HttpUrl

val HttpUrl.path : String get() = Joiner.on('/').join(pathSegments())
