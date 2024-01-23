package misk.api

import misk.web.DispatchMechanism
import okhttp3.Headers
import okhttp3.HttpUrl
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the current incoming request. Misk makes an instance of this interface available in the action scope
 * with the `HttpRequest` key, for use in interceptors, other action scope providers, etc..
 */
@ApiStatus.Experimental
interface HttpRequest {
  val url: HttpUrl
  val dispatchMechanism: DispatchMechanism
  /** HTTP request headers that may be modified via interception. */
  var requestHeaders: Headers
}
