package misk.web.mdc

import javax.servlet.http.HttpServletRequest

interface LogContextProvider {
  fun get(request: HttpServletRequest): String?
}
