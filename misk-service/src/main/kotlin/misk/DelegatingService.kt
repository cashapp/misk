package misk

import com.google.common.util.concurrent.Service

/**
 * Interface to retrieve the underlying [Service] of a wrapper [Service]
 */
@Suppress("UnstableApiUsage") // Guava's Service is @Beta.
interface DelegatingService : Service {
  val service: Service
}
