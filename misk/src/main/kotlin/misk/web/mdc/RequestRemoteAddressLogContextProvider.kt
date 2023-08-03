package misk.web.mdc

import jakarta.inject.Inject
import javax.servlet.http.HttpServletRequest

internal class RequestRemoteAddressLogContextProvider @Inject constructor() : LogContextProvider {
  override fun get(request: HttpServletRequest) = "${request.remoteAddr}:${request.remotePort}"
}
