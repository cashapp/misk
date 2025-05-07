package com.squareup.exemplar.audit

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import jakarta.inject.Inject
import misk.MiskCaller
import misk.audit.AuditClient
import misk.audit.AuditClientConfig
import misk.config.AppName
import misk.scope.ActionScoped
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import wisp.deployment.Deployment
import wisp.logging.getLogger
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

@OptIn(ExperimentalStdlibApi::class)
class ExemplarAuditClient @Inject constructor(
  moshi: Moshi,
  private val config: AuditClientConfig,
  private val clock: Clock,
  private val callerProvider: ActionScoped<MiskCaller?>,
  private val deployment: Deployment,
  @AppName private val appName: String,
) : AuditClient {
  private val okHttpClient = OkHttpClient()
  private val adapter = moshi.adapter<Event>()
  private val region = "us-west-2"

  override fun logEvent(
    target: String,
    description: String,
    automatedChange: Boolean,
    richDescription: String?,
    detailURL: String?,
    approverLDAP: String?,
    requestorLDAP: String?,
    applicationName: String?,
    environment: String?,
    timestampSent: Instant?,
  ) {
    val event = Event(
      eventSource = appName,
      eventTarget = target,
      timestampSent = (timestampSent ?: clock.instant()).toEpochMilli().nanoseconds.toInt(DurationUnit.NANOSECONDS),
      applicationName = applicationName ?: appName,
      approverLDAP = approverLDAP ?: callerProvider.get()?.principal,
      automatedChange = automatedChange,
      description = description,
      richDescription = richDescription,
      environment = environment ?: deployment.mapToEnvironmentName(),
      detailURL = detailURL,
      region = region,
      requestorLDAP = requestorLDAP ?: callerProvider.get()?.principal,
    )

    try {
      val json = adapter.toJson(event)
      val request = Request.Builder()
        .url(config.url)
        .post(json.toRequestBody("application/json".toMediaType()))
        .build()

      val response = okHttpClient.newCall(request).execute()
      if (!response.isSuccessful) {
        logger.error("Failed to send audit event [event=$event][response=$response]")
      }
    } catch (e: Exception) {
      logger.error("Failed to send audit event [event=$event][exception=$e]", e)
    }
  }

  companion object {
    private val logger = getLogger<ExemplarAuditClient>()
  }
}
