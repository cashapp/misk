package misk.web.actions

import misk.web.Get
import misk.web.PlaintextResponseBody
import misk.web.Response
import javax.inject.Singleton

@Singleton
class NotFoundAction : WebAction {
    @Get("/{path:.*}")
    @PlaintextResponseBody
    fun notFound(path: String): Response<String> {
        return Response(
                body = "Nothing found at /$path",
                statusCode = 404
        )
    }
}
