package misk.web.mdc

import javax.inject.Inject
import javax.servlet.http.HttpServletRequest

internal class RequestRemoteAddressLogContextProvider @Inject constructor() : LogContextProvider {
  override fun get(request: HttpServletRequest) = "${request.remoteAddr}:${request.remotePort}"
}
