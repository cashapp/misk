package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class AwsSqsQueueAttributeImporterTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module = SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client)

  @Inject private lateinit var sqs: AmazonSQS
  @Inject private lateinit var queue: JobQueue
  @Inject private lateinit var importer: AwsSqsQueueAttributeImporter
  @Inject private lateinit var sqsMetrics: SqsMetrics

  private lateinit var queueName: QueueName

  @BeforeEach fun createQueues() {
    // Ensure that each test case runs on a unique queue
    queueName = QueueName("sqs_job_queue_test")
    sqs.createQueue(CreateQueueRequest()
        .withQueueName(queueName.value)
        .withAttributes(mapOf(
            // 1 second visibility timeout
            "VisibilityTimeout" to 1.toString())
        ))
  }

  @Test fun importQueueAttributes() {
    importer.import(queueName)
    queue.enqueue(queueName, "ok")
    queue.enqueue(queueName, "ok")
    queue.enqueue(queueName, "ok")
    queue.enqueue(queueName, "ok")

    Thread.sleep(100)
    Assertions.assertThat(sqsMetrics.sqsApproxNumberOfMessages.labels(queueName.value,
        queueName.value).get()).isEqualTo(4.0)
  }
}