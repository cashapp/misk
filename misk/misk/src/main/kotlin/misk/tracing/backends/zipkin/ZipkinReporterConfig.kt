package misk.tracing.backends.zipkin

import misk.config.Config

data class ZipkinReporterConfig(
  /**
   * Maximum backlog of span bytes reported vs sent. Corresponds to ReporterMetrics.updateQueuedBytes.
   * Default 1% of heap
   */
  val queued_max_bytes: Int?,

  /**
   * Maximum bytes sendable per message including overhead. Default Sender.messageMaxBytes (5242880)
   */
  val message_max_bytes: Int?,

  /**
   * Maximum time to wait for messageMaxBytes to accumulate before sending. Default 1 second
   */
  val message_timeout_sec: Long?
) : Config