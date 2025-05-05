package misk.web.interceptors.hooks

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.Action
import misk.MiskCaller
import misk.audit.AuditClient
import misk.audit.AuditRequestResponse
import misk.web.HttpCall
import misk.web.interceptors.RequestResponseBody
import java.time.Duration
import kotlin.reflect.full.findAnnotation

/**
 * This hook is not installed by default but is best installed in the module which binds the
 *    implementation of the [AuditClient].
 *
 * ```kotlin
 * multibind<RequestResponseHook.Factory>().to<AuditClientHook.Factory>()
 * ```
 */
class AuditClientHook private constructor(
  private val action: Action,
  private val auditClient: AuditClient,
  private val annotation: AuditRequestResponse,
) : RequestResponseHook {

  @Singleton
  class Factory @Inject constructor(
    private val auditClient: AuditClient,
  ) : RequestResponseHook.Factory {
    override fun create(action: Action): RequestResponseHook? {
      val annotation = action.function.findAnnotation<AuditRequestResponse>()
        ?: return null

      return AuditClientHook(action, auditClient, annotation)
    }
  }

  override fun handle(
    caller: MiskCaller?,
    httpCall: HttpCall,
    requestResponse: RequestResponseBody?,
    elapsed: Duration,
    elapsedToString: String,
    error: Throwable?
  ) {
    val principal = caller?.principal ?: "unknown"
    val description = buildDescription(
      caller = caller,
      httpCall = httpCall,
      elapsedToString = elapsedToString,
      requestResponse = requestResponse,
      error = error,
    )

    val code = httpCall.statusCode
    val statusCode = if (code > 299) "code=$code " else ""
    auditClient.logEvent(
      target = annotation.target.ifBlank { action.name },
      description = annotation.description.ifBlank { "${action.name} ${statusCode}principal=${principal}" },
      automatedChange = annotation.automatedChange,
      richDescription = if (annotation.richDescription.isBlank()) { description } else { annotation.richDescription + " " + description },
      detailURL = annotation.detailURL.ifBlank { null },
      applicationName = annotation.applicationName.ifBlank { null },
    )
  }

  private fun buildDescription(
    caller: MiskCaller?,
    httpCall: HttpCall,
    elapsedToString: String,
    requestResponse: RequestResponseBody?,
    error: Throwable?,
  ): String = buildString {
    val principal = caller?.principal ?: "unknown"

    append("${action.name} principal=$principal time=${elapsedToString}")

    val statusCode = httpCall.statusCode
    if (error != null) {
      append(" failed")
    } else {
      append(" code=$statusCode")
    }
    
    requestResponse?.let {
      requestResponse.request?.let {
        if (annotation.includeRequest) {
          append(" request=${requestResponse.request}")
        }
      }
      requestResponse.requestHeaders?.let {
        if (annotation.includeRequestHeaders) {
          append(" requestHeaders=${requestResponse.requestHeaders}")
        }
      }
      requestResponse.response?.let {
        if (annotation.includeResponse) {
          append(" response=${requestResponse.response}")
        }
      }
      requestResponse.responseHeaders?.let {
        if (annotation.includeReseponseHeaders) {
          append(" responseHeaders=${requestResponse.responseHeaders}")
        }
      }
    }
  }
}
