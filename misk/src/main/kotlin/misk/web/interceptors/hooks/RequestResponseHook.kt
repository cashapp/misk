package misk.web.interceptors.hooks

import misk.Action
import misk.MiskCaller
import misk.web.HttpCall
import misk.web.interceptors.RequestLoggingTransformer
import misk.web.interceptors.RequestResponseBody
import java.time.Duration

/**
 * Functionality which takes a complete request and response from a action call.
 */
interface RequestResponseHook {
  fun handle(
    caller: MiskCaller?,
    httpCall: HttpCall,
    requestResponse: RequestResponseBody?,
    elapsed: Duration,
    elapsedToString: String,
    error: Throwable?
  )

  interface Factory {
    fun create(action: Action): RequestResponseHook?
  }
}
