package misk.web.mdc

import javax.inject.Inject
import javax.servlet.http.HttpServletRequest

internal class RequestProtocolLogContextProvider @Inject constructor() : LogContextProvider {
  override fun get(request: HttpServletRequest): String? = request.protocol
}
