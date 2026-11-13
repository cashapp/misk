package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.google.inject.util.Modules
import jakarta.inject.Inject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import misk.feature.testing.FakeFeatureFlags
import misk.inject.FakeSwitch
import misk.inject.FakeSwitchModule
import misk.jobqueue.JobConsumer
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.jobqueue.sqs.SqsJobConsumer.Companion.CONSUMERS_BATCH_SIZE
import misk.jobqueue.subscribe
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class SqsAsyncSwitchTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule
  private val module =
    Modules.override(
      SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client),
    ).with(FakeSwitchModule())
  @Inject private lateinit var sqs: AmazonSQS
  @Inject private lateinit var queue: JobQueue
  @Inject private lateinit var consumer: JobConsumer
  @Inject private lateinit var fakeSwitch: FakeSwitch
  @Inject private lateinit var fakeFeatureFlags: FakeFeatureFlags

  private val queueName = QueueName("sqs_async_switch_test")

  @BeforeEach
  fun setUp() {
    sqs.createQueue(queueName.value)
    fakeFeatureFlags.override(CONSUMERS_BATCH_SIZE, 10)
    fakeSwitch.enabledKeys.add("sqs")
  }

  @Test
  fun `jobs are not handled when async switch is disabled`() {
    val jobCount = java.util.concurrent.atomic.AtomicInteger(0)
    val jobRan = CountDownLatch(1)

    consumer.subscribe(queueName) { job ->
      jobCount.incrementAndGet()
      job.acknowledge()
      jobRan.countDown()
    }

    // Disable SQS — enqueue a job, it should NOT be handled
    fakeSwitch.enabledKeys.remove("sqs")
    queue.enqueue(queueName, "should not be handled yet")

    // Wait a bit — job should NOT have been handled
    assertThat(jobRan.await(2, TimeUnit.SECONDS)).isFalse()
    assertThat(jobCount.get()).isEqualTo(0)

    // Re-enable SQS — the job should now be handled
    fakeSwitch.enabledKeys.add("sqs")
    assertThat(jobRan.await(10, TimeUnit.SECONDS)).isTrue()
    assertThat(jobCount.get()).isEqualTo(1)
  }
}
