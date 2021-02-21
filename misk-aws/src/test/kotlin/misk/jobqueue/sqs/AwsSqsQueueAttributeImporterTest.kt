package misk.jobqueue.sqs

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.CreateQueueRequest
import misk.jobqueue.JobQueue
import misk.jobqueue.QueueName
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
internal class AwsSqsQueueAttributeImporterTest {
  @MiskExternalDependency private val dockerSqs = DockerSqs
  @MiskTestModule private val module =
    SqsJobQueueTestModule(dockerSqs.credentials, dockerSqs.client)

  @Inject private lateinit var sqs: AmazonSQS
  @Inject private lateinit var queue: JobQueue
  @Inject private lateinit var importer: AwsSqsQueueAttributeImporter
  @Inject private lateinit var sqsMetrics: SqsMetrics

  private lateinit var queueName: QueueName

  @BeforeEach fun createQueues() {
    // Ensure that each test case runs on a unique queue
    queueName = QueueName("sqs_job_queue_test")
    sqs.createQueue(
      CreateQueueRequest()
        .withQueueName(queueName.value)
        .withAttributes(
          mapOf(
            // 1 second visibility timeout
            "VisibilityTimeout" to 1.toString()
          )
        )
    )
  }

  @Test fun importQueueAttributes() {
    importer.import(queueName)
    queue.enqueue(queueName, "ok")
    queue.enqueue(queueName, "ok")
    queue.enqueue(queueName, "ok")
    queue.enqueue(queueName, "ok")

    await()
      .atMost(1, TimeUnit.SECONDS)
      .until {
        sqsMetrics.sqsApproxNumberOfMessages.labels(
          AwsSqsQueueAttributeImporter.metricNamespace,
          AwsSqsQueueAttributeImporter.metricStat,
          queueName.value,
          queueName.value
        ).get() == 4.0
      }
  }
}
