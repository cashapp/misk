package misk

import com.google.common.util.concurrent.Service

/**
 * Interface to retrieve the underlying [Service] of a wrapper [Service]
 */
interface DelegatingService : Service {
  val service: Service
}
