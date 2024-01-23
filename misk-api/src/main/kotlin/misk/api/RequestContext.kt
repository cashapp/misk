package misk.api

import misk.web.DispatchMechanism
import okhttp3.Headers
import okhttp3.HttpUrl

/**
 * Represents the current request. Misk makes an instance of this interface available in the action scope with the
 * `RequestContext` key, for use in interceptors, other action scope providers, etc..
 */
interface RequestContext {
  val url: HttpUrl
  val dispatchMechanism: DispatchMechanism
  /** HTTP request headers that may be modified via interception. */
  var requestHeaders: Headers
}
