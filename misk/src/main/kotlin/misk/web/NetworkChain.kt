package misk.web

import misk.Action
import misk.web.actions.WebAction

interface NetworkChain {
  /** The live HTTP call. You can access hot streams on this call. */
  val httpCall: HttpCall

  /** The action for this call. */
  val action: Action

  /** The action instance this call is routing to. */
  val webAction: WebAction

  /** Invoke the next call in the interceptor chain. */
  fun proceed(httpCall: HttpCall)
}
