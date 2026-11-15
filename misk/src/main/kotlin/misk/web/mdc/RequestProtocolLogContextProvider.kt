package misk.web.mdc

import jakarta.inject.Inject
import jakarta.servlet.http.HttpServletRequest

internal class RequestProtocolLogContextProvider @Inject constructor() : LogContextProvider {
  override fun get(request: HttpServletRequest): String? = request.protocol
}
