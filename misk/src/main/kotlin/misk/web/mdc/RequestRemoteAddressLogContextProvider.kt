package misk.web.mdc

import com.google.inject.Inject
import javax.servlet.http.HttpServletRequest

internal class RequestRemoteAddressLogContextProvider @Inject constructor() : LogContextProvider {
  override fun get(request: HttpServletRequest) = "${request.remoteAddr}:${request.remotePort}"
}
