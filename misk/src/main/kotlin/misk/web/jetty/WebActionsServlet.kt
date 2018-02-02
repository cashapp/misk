package misk.web.jetty

import misk.inject.keyOf
import misk.logging.getLogger
import misk.scope.ActionScope
import misk.web.BoundAction
import misk.web.Request
import misk.web.actions.WebAction
import misk.web.asRequest
import javax.inject.Inject
import javax.inject.Singleton
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private val logger = getLogger<WebActionsServlet>()

@Singleton
internal class WebActionsServlet @Inject constructor(
        private val boundActions: MutableSet<BoundAction<out WebAction, *>>,
        private val scope: ActionScope
) : HttpServlet() {
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
        handleCall(request, response)
    }

    override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
        handleCall(request, response)
    }

    private fun handleCall(request: HttpServletRequest, response: HttpServletResponse) {
        val seedData = mapOf(
                keyOf<HttpServletRequest>() to request,
                keyOf<Request>() to request.asRequest())

        scope.enter(seedData).use {
            val candidateActions = boundActions.mapNotNull { it.match(request) }
            val bestAction = candidateActions.sorted().firstOrNull()
            if (bestAction != null) {
                bestAction.handle(request, response)
                logger.debug("Request handled by WebActionServlet")
            }
        }
    }
}
