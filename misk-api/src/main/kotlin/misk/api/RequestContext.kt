package misk.api

import misk.web.DispatchMechanism
import okhttp3.HttpUrl

interface RequestContext {
  val url: HttpUrl
  val dispatchMechanism: DispatchMechanism
  val requestHeadersContext: RequestHeadersContext
}

interface RequestHeadersContext {
  operator fun get(name: String): String?

  fun asIterable(): Iterable<Pair<String, String>>
}
