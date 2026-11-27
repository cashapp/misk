package misk.audit

import java.time.Duration
import okhttp3.Request

/** A single-use registration for one HTTP dispatch. Register and apply it before sending the request. */
class FakeAuditRequestCapture
internal constructor(
  private val captures: AuditRequestCaptures,
  private val registration: AuditRequestCaptures.Registration,
) : AutoCloseable {
  /** Adds private correlation to this request. Retries and redirects require a new capture. */
  fun applyTo(requestBuilder: Request.Builder) {
    captures.applyTo(registration, requestBuilder)
  }

  /**
   * Waits for the downstream HTTP chain, including executed synchronous request/response hooks.
   *
   * The snapshot contains actual fake recordings, not proof that every hook succeeded. Misk can swallow hook failures
   * before the fake is reached. Detached producers and work outside that chain are excluded. A timeout fails with
   * NOT_OBSERVED or IN_FLIGHT diagnostics; it never establishes an empty result. Repeated waits return the same
   * snapshot without consuming [FakeAuditClient.sentEvents].
   *
   * Recording failures, escaping dispatch failures, close, and reset fail with `ExecutionException`, wrapping the
   * original recording/dispatch failure or the capture lifecycle failure as its cause.
   */
  fun awaitCompleted(timeout: Duration): RecordedAuditRequest = captures.awaitCompleted(registration, timeout)

  /** Cancels an unfinished capture. A running dispatch remains tracked until its chain exits. */
  override fun close() {
    captures.close(registration)
  }
}
