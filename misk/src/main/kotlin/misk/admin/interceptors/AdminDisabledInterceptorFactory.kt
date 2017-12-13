package misk.admin.interceptors

import misk.Action
import misk.Chain
import misk.Interceptor
import misk.admin.actions.AdminAction
import misk.web.Response
import misk.web.ResponseBody
import misk.web.WebConfig
import okio.BufferedSink
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.full.findAnnotation

@Singleton
class AdminDisabledInterceptorFactory @Inject constructor(
    private val webConfig: WebConfig
) : Interceptor.Factory {
    override fun create(action: Action): Interceptor? {
        return if (action.function.findAnnotation<AdminAction>() != null
                && !webConfig.admin_enabled) {
            AdminDisabledInterceptor
        } else {
            null
        }
    }

    object AdminDisabledInterceptor : Interceptor {
        override fun intercept(chain: Chain): Any? {
            return Response(body = object : ResponseBody {
                override fun writeTo(sink: BufferedSink) {
                    sink.writeUtf8("Admin pages are currently not enabled")
                }
            })
        }
    }
}
