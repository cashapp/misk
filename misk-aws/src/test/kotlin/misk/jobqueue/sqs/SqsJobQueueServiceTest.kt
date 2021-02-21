package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.google.common.util.concurrent.AbstractService
import com.google.common.util.concurrent.ServiceManager
import com.google.inject.util.Modules
import misk.ServiceModule
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.subscribe
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** This is separate from [SqsJobQueueTest] because we don't want the services started automatically. */
@MiskTest
internal class SqsJobQueueServiceTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module =
    Modules.combine(
      SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client),
      ServiceModule<ManualStartService>()
    )
  @Inject private lateinit var sqs: AmazonSQS
  @Inject private lateinit var queue: JobQueue
  @Inject private lateinit var consumer: JobConsumer
  @Inject private lateinit var serviceManager: ServiceManager
  @Inject private lateinit var manualStartService: ManualStartService

  private val queueName = QueueName("sqs_job_queue_service_test")

  @BeforeEach fun createQueues() {
    sqs.createQueue(queueName.value)
  }

  @AfterEach
  internal fun tearDown() {
    serviceManager.stopAsync()
    serviceManager.awaitStopped(20, TimeUnit.SECONDS)
  }

  @Test fun `jobs are not handled until all services are running`() {
    val log = LinkedBlockingDeque<String>()
    val jobRan = CountDownLatch(1)

    log.put("about to subscribe")
    consumer.subscribe(queueName) { job ->
      log.put("handling job")
      job.acknowledge()
      jobRan.countDown()
    }

    // This job should not run until all the services are healthy, meaning we should see the "about to enqueue" message
    // now, and the "handling job" message at the end
    log.put("about to enqueue")
    queue.enqueue(queueName, "this is a job")

    log.put("about to start the service manager")
    serviceManager.startAsync()

    log.put("about to start the manual start service")
    manualStartService.manualStart()

    assertThat(log.poll()).isEqualTo("about to subscribe")
    assertThat(log.poll()).isEqualTo("about to enqueue")
    assertThat(log.poll()).isEqualTo("about to start the service manager")
    assertThat(log.poll()).isEqualTo("about to start the manual start service")

    // Wait for the job consumer to run and execute the job
    jobRan.await(1, TimeUnit.SECONDS)
    assertThat(log.poll()).isEqualTo("handling job") // Called by the handler thread
  }

  @Singleton
  class ManualStartService @Inject constructor() : AbstractService() {
    override fun doStart() {
      // Note this doesn't call NotifyStarted.
    }

    fun manualStart() {
      notifyStarted()
    }

    override fun doStop() {
      notifyStopped()
    }
  }
}
