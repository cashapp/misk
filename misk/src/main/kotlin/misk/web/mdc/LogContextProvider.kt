package misk.web.mdc

import jakarta.servlet.http.HttpServletRequest

interface LogContextProvider {
  fun get(request: HttpServletRequest): String?
}
