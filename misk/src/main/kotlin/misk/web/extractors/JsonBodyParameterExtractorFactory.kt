package misk.web.extractors

import misk.web.JsonRequestBody
import misk.web.PathPattern
import misk.web.Request
import misk.web.actions.WebAction
import com.squareup.moshi.Moshi
import java.util.regex.Matcher
import javax.inject.Inject
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaType

internal class JsonBodyParameterExtractorFactory @Inject constructor(
    private val moshi: Moshi
) : ParameterExtractor.Factory {
    override fun create(parameter: KParameter, pathPattern: PathPattern): ParameterExtractor? {
        if (parameter.findAnnotation<JsonRequestBody>() == null) return null

        val adapter = moshi.adapter<Any>(parameter.type.javaType)

        return object : ParameterExtractor {
            override fun extract(webAction: WebAction, request: Request, pathMatcher: Matcher): Any? {
                return adapter.fromJson(request.body)
            }
        }
    }
}
