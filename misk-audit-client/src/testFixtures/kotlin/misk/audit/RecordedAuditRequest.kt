package misk.audit

/** An immutable snapshot of the recordings made within one completed HTTP request chain. */
class RecordedAuditRequest internal constructor(val statusCode: Int, val events: List<FakeAuditClient.FakeAuditEvent>)
