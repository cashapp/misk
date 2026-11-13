package misk.audit

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant
import java.util.concurrent.LinkedBlockingDeque
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import misk.MiskCaller
import misk.config.AppName
import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.testing.FakeFixture
import misk.time.FakeClock
import wisp.deployment.Deployment
import wisp.deployment.TESTING

@Singleton
class FakeAuditClient @Inject constructor(private val optionalBinder: OptionalBinder) : AuditClient, FakeFixture() {
  private val region = "us-west-2"
  internal val requestCaptures = AuditRequestCaptures()

  /** Registers one request before sending it. Requires [FakeAuditRequestCompletionModule]. */
  fun captureHttpRequest(): FakeAuditRequestCapture {
    check(optionalBinder.requestContext != null) {
      "Install FakeAuditRequestCompletionModule before capturing HTTP requests"
    }
    return requestCaptures.register()
  }

  override fun reset() {
    requestCaptures.reset { super.reset() }
  }

  val sentEvents: LinkedBlockingDeque<FakeAuditEvent> by resettable { LinkedBlockingDeque<FakeAuditEvent>() }
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
    applicationName: String?,
    environment: String?,
    timestampSent: Instant?,
  ) {
    requestCaptures.record(optionalBinder.requestContext?.getIfInScope()?.claimedRegistration) {
      val recordedEvent =
        FakeAuditEvent(
          eventSource = optionalBinder.appName,
          eventTarget = target,
          timestampSent =
            (timestampSent ?: optionalBinder.clock.instant())
              .toEpochMilli()
              .nanoseconds
              .toInt(DurationUnit.NANOSECONDS),
          applicationName = applicationName ?: optionalBinder.appName,
          approverLDAP = approverLDAP,
          automatedChange = automatedChange,
          description = description,
          richDescription = richDescription,
          environment = environment ?: optionalBinder.deployment.mapToEnvironmentName(),
          detailURL = detailURL,
          region = region,
          requestorLDAP =
            requestorLDAP
              ?: if (automatedChange) {
                null
              } else {
                optionalBinder.callerProvider.getIfInScope()?.principal ?: DEFAULT_USER
              },
        )

      sentEvents.add(recordedEvent)

      if (enableLogging) {
        logger.info("Audit Event Logged [event=$recordedEvent]")
      }
      recordedEvent
    }
  }

  companion object {
    private val logger = getLogger<FakeAuditClient>()
    const val DEFAULT_USER = "default-user"
  }

  /**
   * https://github.com/google/guice/wiki/FrequentlyAskedQuestions#how-can-i-inject-optional-parameters-into-a-constructor
   */
  @Singleton
  class OptionalBinder @Inject constructor() {
    @com.google.inject.Inject(optional = true) internal var requestContext: ActionScoped<AuditRequestContext>? = null

    @com.google.inject.Inject(optional = true) @AppName var appName: String = "test-app"

    @com.google.inject.Inject(optional = true)
    var callerProvider: ActionScoped<MiskCaller?> = ActionScoped.of(MiskCaller(user = DEFAULT_USER))

    @com.google.inject.Inject(optional = true) var clock: Clock = FakeClock()

    @com.google.inject.Inject(optional = true) var deployment: Deployment = TESTING
  }
}
