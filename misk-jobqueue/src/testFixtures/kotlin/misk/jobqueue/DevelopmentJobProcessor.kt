package misk.jobqueue;

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.ServiceManager
import misk.tasks.RepeatedTaskQueue
import misk.tasks.Status
import java.time.Duration
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
internal class DevelopmentJobProcessor @Inject constructor(
  @ForDevelopmentHandling private val taskQueue: RepeatedTaskQueue,
  private val fakeJobQueue: FakeJobQueue,
  private val serviceManagerProvider: Provider<ServiceManager>,
) : AbstractIdleService() {
  override fun startUp() {
    taskQueue.scheduleWithBackoff(Duration.ZERO) {
      // Don't call handlers until all services are ready, otherwise handlers will crash because
      // the services they might need (databases, etc.) won't be ready.
      if (serviceManagerProvider.get().isHealthy) {
        fakeJobQueue.handleJobs(assertAcknowledged = false, considerDelays = true)
        Status.OK
      } else {
        Status.NO_WORK
      }
    }
  }

  override fun shutDown() {}
}
