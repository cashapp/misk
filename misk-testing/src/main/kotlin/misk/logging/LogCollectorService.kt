package misk.logging

import com.google.common.util.concurrent.Service

/** Marker interface for the service that produces a [LogCollector]. */
interface LogCollectorService : Service
