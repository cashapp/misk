package misk.audit

import java.time.Duration
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeoutException
import misk.web.HttpCall
import okhttp3.Request

internal class AuditRequestCaptures {
  private val lock = Any()
  private var generation = 0L
  private val registrations = mutableMapOf<String, Registration>()

  internal class Registration(val id: String, val generation: Long) {
    val completed = CompletableFuture<RecordedAuditRequest>()
    val events = mutableListOf<FakeAuditClient.FakeAuditEvent>()
    var applied = false
    var claimed = false
    var finished = false
    var closed = false
    var failure: Throwable? = null
  }

  fun register(): FakeAuditRequestCapture =
    synchronized(lock) {
      val registration = Registration(UUID.randomUUID().toString(), generation)
      registrations[registration.id] = registration
      FakeAuditRequestCapture(this, registration)
    }

  fun applyTo(registration: Registration, requestBuilder: Request.Builder) =
    synchronized(lock) {
      check(registration.generation == generation && !registration.closed) { "Audit capture is closed or reset" }
      check(!registration.applied) { "Audit capture can only be applied once; retries and redirects are unsupported" }
      check(requestBuilder.build().headers.values(HEADER).isEmpty()) {
        "Request already contains audit capture headers"
      }
      registration.applied = true
      requestBuilder.header(HEADER, registration.id)
      Unit
    }

  fun claim(httpCall: HttpCall): Registration? =
    synchronized(lock) {
      val captureIds = httpCall.requestHeaders.values(HEADER)
      httpCall.requestHeaders = httpCall.requestHeaders.newBuilder().removeAll(HEADER).build()
      if (captureIds.isEmpty()) return null
      check(captureIds.size == 1) { "Duplicate audit capture headers" }
      val registration =
        checkNotNull(registrations[captureIds.single()]) { "Unknown, reset or previously used audit capture ID" }
      check(!registration.closed && registration.applied && !registration.claimed) {
        "Audit capture ID already used or closed; retries and redirects are unsupported"
      }
      registration.claimed = true
      registration
    }

  fun record(registration: Registration?, recordEvent: () -> FakeAuditClient.FakeAuditEvent) =
    synchronized(lock) {
      if (registration != null) {
        check(registration.generation == generation && !registration.finished) {
          "Audit recording arrived after request completion or fixture reset"
        }
      }
      try {
        val recordedEvent = recordEvent()
        registration?.events?.add(recordedEvent)
      } catch (recordingFailure: Throwable) {
        if (registration != null) registration.failure = registration.failure ?: recordingFailure
        throw recordingFailure
      }
    }

  fun finish(registration: Registration, statusCode: Int, dispatchFailure: Throwable?) =
    synchronized(lock) {
      registration.finished = true
      if (registration.closed) registrations.remove(registration.id, registration)
      val failure = dispatchFailure ?: registration.failure
      if (failure != null) {
        registration.completed.completeExceptionally(failure)
      } else {
        val snapshot = Collections.unmodifiableList(ArrayList(registration.events))
        registration.completed.complete(RecordedAuditRequest(statusCode, snapshot))
      }
      Unit
    }

  fun awaitCompleted(registration: Registration, timeout: Duration): RecordedAuditRequest {
    require(!timeout.isNegative) { "timeout must not be negative" }
    try {
      return registration.completed.get(timeout.toNanos(), NANOSECONDS)
    } catch (timeoutFailure: TimeoutException) {
      val phase = synchronized(lock) { if (registration.claimed) "IN_FLIGHT" else "NOT_OBSERVED" }
      throw TimeoutException("Audit HTTP request $phase: request chain did not complete within $timeout")
    }
  }

  fun close(registration: Registration) =
    synchronized(lock) {
      registration.closed = true
      if (!registration.claimed || registration.finished) registrations.remove(registration.id, registration)
      if (!registration.finished) {
        registration.completed.completeExceptionally(IllegalStateException("Audit capture closed before completion"))
      }
      Unit
    }

  fun reset(resetLegacyState: () -> Unit) =
    synchronized(lock) {
      check(registrations.values.none { it.claimed && !it.finished }) {
        "Cannot reset with audit HTTP requests IN_FLIGHT"
      }
      registrations.values
        .filter { !it.finished }
        .forEach {
          it.completed.completeExceptionally(IllegalStateException("Audit capture reset before request was observed"))
        }
      registrations.clear()
      generation++
      resetLegacyState()
    }

  companion object {
    const val HEADER = "X-Misk-Test-Audit-Capture"
  }
}
