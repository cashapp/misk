package misk.web.mdc

import com.google.inject.Inject
import javax.servlet.http.HttpServletRequest

internal class RequestProtocolLogContextProvider @Inject constructor() : LogContextProvider {
  override fun get(request: HttpServletRequest): String? = request.protocol
}
