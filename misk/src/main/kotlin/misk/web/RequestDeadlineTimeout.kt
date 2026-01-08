package misk.web

import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget

/**
 * Specifies a deadline timeout for this web action.
 *
 * This gets set as the request deadline if no request deadline headers exist, in
 * [RequestDeadlineInterceptor][misk.web.interceptors.RequestDeadlineInterceptor]. The request deadline is checked and
 * enforced in the client interceptor [DeadlinePropagationInterceptor][misk.client.DeadlinePropagationInterceptor] for
 * outgoing requests and propagated to its downstream.
 *
 * @param timeoutMs The timeout duration in milliseconds
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class RequestDeadlineTimeout(val timeoutMs: Long)
