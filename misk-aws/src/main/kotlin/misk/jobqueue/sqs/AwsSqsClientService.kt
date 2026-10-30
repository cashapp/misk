package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.google.common.util.concurrent.AbstractIdleService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.cloud.aws.AwsRegion
import misk.logging.getLogger

/**
 * A service wrapper for [AmazonSQS] clients that ensures proper shutdown ordering.
 *
 * This service doesn't do anything on startup, but on shutdown it closes the SQS clients.
 * By making other services (like [misk.tasks.RepeatedTaskQueue]) depend on this service,
 * we ensure those services complete their in-flight tasks before the HTTP connection pool
 * is closed.
 *
 * @param shutdownClients If false, the clients will not be shut down when this service stops.
 *   This is useful for testing where clients are shared across tests.
 */
@Singleton
open class AwsSqsClientService @Inject constructor(
  private val sqsClient: AmazonSQS,
  @ForSqsReceiving private val sqsReceivingClient: AmazonSQS,
  private val crossRegionSQS: Map<AwsRegion, AmazonSQS>,
  @ForSqsReceiving private val crossRegionForReceivingSQS: Map<AwsRegion, AmazonSQS>,
) : AbstractIdleService() {

  private var shutdownClients: Boolean = true

  fun withShutdownClients(shutdownClients: Boolean): AwsSqsClientService {
    this.shutdownClients = shutdownClients
    return this
  }

  override fun startUp() {
    log.info { "AWS SQS client service started" }
  }

  override fun shutDown() {
    if (!shutdownClients) {
      log.info { "AWS SQS client service stopped (clients not shut down)" }
      return
    }

    log.info { "Shutting down AWS SQS clients" }

    val allClients = buildSet {
      add(sqsClient)
      add(sqsReceivingClient)
      addAll(crossRegionSQS.values)
      addAll(crossRegionForReceivingSQS.values)
    }

    val errors = mutableListOf<Exception>()
    for (client in allClients) {
      try {
        client.shutdown()
      } catch (e: Exception) {
        log.error(e) { "Error shutting down AWS SQS client" }
        errors.add(e)
      }
    }

    log.info { "AWS SQS clients shut down" }

    if (errors.isNotEmpty()) {
      val first = errors.removeFirst()
      errors.forEach { first.addSuppressed(it) }
      throw first
    }
  }

  companion object {
    private val log = getLogger<AwsSqsClientService>()
  }
}
