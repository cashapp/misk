package misk.web.jetty

import misk.logging.getLogger
import misk.web.BoundAction
import misk.web.actions.WebAction
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private val logger = getLogger<WebActionsServlet>()

@Singleton
internal class WebActionsServlet @Inject constructor(
  private val boundActions: MutableSet<BoundAction<out WebAction, *>>
) : HttpServlet() {
  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    handleCall(request, response)
  }

  private fun handleCall(request: HttpServletRequest, response: HttpServletResponse) {
    if (boundActions.any { it.tryHandle(request, response) }) {
      logger.debug("Request handled by WebActionServlet")
    }
  }
}
