package misk.audit

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskCaller
import misk.config.AppName
import misk.scope.ActionScoped
import misk.testing.FakeFixture
import wisp.deployment.Deployment
import wisp.logging.getLogger
import java.time.Clock
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

@Singleton
class FakeAuditClient @Inject constructor(
  @AppName private val appName: String,
  private val callerProvider: ActionScoped<MiskCaller?>,
  private val clock: Clock,
  private val deployment: Deployment,
): AuditClient, FakeFixture() {
  private val region: String = "us-west-2"
  val sentEvents: MutableList<FakeAuditEvent> by resettable {  mutableListOf() }
  var enableLogging: Boolean by resettable { false }

  data class FakeAuditEvent(
    val eventSource: String,
    val eventTarget: String,
    val timestampSent: Int,
    val applicationName: String,
    val approverLDAP: String?,
    val automatedChange: Boolean,
    val description: String,
    val richDescription: String?,
    val environment: String,
    val detailURL: String?,
    val region: String,
    val requestorLDAP: String?,
  )

  override fun logEvent(
    target: String,
    description: String,
    automatedChange: Boolean,
    richDescription: String?,
    detailURL: String?,
    approverLDAP: String?,
    requestorLDAP: String?,
    applicationName: String?
  ) {
    val event =
      FakeAuditEvent(
        eventSource = appName,
        eventTarget = target,
        timestampSent = clock.instant().toEpochMilli().nanoseconds.toInt(DurationUnit.NANOSECONDS),
        applicationName = applicationName ?: appName,
        approverLDAP = approverLDAP ?: callerProvider.get()?.principal,
        automatedChange = automatedChange,
        description = description,
        richDescription = richDescription,
        environment = deployment.mapToEnvironmentName(),
        detailURL = detailURL,
        region = region,
        requestorLDAP = requestorLDAP ?: callerProvider.get()?.principal,
      )

    sentEvents.add(event)

    if (enableLogging) {
      logger.info("Audit Event Logged [event=$event]")
    }
  }

  companion object {
    private val logger = getLogger<FakeAuditClient>()
  }

}
