package misk.web.extractors

import misk.web.PathPattern
import misk.web.Request
import misk.web.actions.WebAction
import java.util.regex.Matcher
import kotlin.reflect.KParameter

interface ParameterExtractor {
    fun extract(webAction: WebAction, request: Request, pathMatcher: Matcher): Any?

    interface Factory {
        fun create(parameter: KParameter, pathPattern: PathPattern): ParameterExtractor?
    }
}
