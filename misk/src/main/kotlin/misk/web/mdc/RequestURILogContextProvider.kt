package misk.web.mdc

import jakarta.inject.Inject
import javax.servlet.http.HttpServletRequest

internal class RequestURILogContextProvider @Inject constructor() : LogContextProvider {
  override fun get(request: HttpServletRequest): String? = request.requestURI
}
