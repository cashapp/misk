package misk.aws2.sqs.jobqueue

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import misk.testing.ExternalDependency
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException
import misk.containers.Composer
import misk.containers.Container
import misk.containers.ContainerUtil
import wisp.logging.getLogger
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * A test SQS Service. Tests can connect to the service at 127.0.0.1:[clientPort]
 */
object DockerSqs : ExternalDependency {

  private val log = getLogger<DockerSqs>()
  private const val clientPort = 9324

  override fun beforeEach() {
  }

  /** cleans up the queues after each run */
  override fun afterEach() {
    val queues = client.listQueues()
    val deleteResponses = queues.join().queueUrls().map {
      client.deleteQueue(DeleteQueueRequest.builder().queueUrl(it).build())
    }
    CompletableFuture.allOf(*deleteResponses.toTypedArray()).join()
  }

  private val composer = Composer(
    "e-sqs",
    Container {
      // NB(mmihic): Because the client port is embedded directly into the queue URLs, we have to use
      // the same external port as we do for the internal port
      val exposedClientPort = ExposedPort.tcp(clientPort)
      withImage("softwaremill/elasticmq:1.6.5")
        .withName("sqs")
        .withExposedPorts(exposedClientPort)
        .withHostConfig(
          HostConfig.newHostConfig().withPortBindings(
            Ports().apply { bind(exposedClientPort, Ports.Binding.bindPort(clientPort)) }
          )
        )
    }
  )

  val credentialsProvider = AwsCredentialsProvider {
    AwsBasicCredentials.builder()
      .accessKeyId("access-key-id")
      .secretAccessKey("secret-access-key")
      .accountId(null)
      .providerName(null)
      .build()
  }

  val region = Region.of("us-east-1")
  val endpointUri = URI.create("http://${ContainerUtil.dockerTargetOrLocalIp()}:$clientPort")
  val client = SqsAsyncClient.builder()
    .endpointOverride(endpointUri)
    .credentialsProvider(credentialsProvider)
    .region(region)
    .build()

  override fun startup() {
    composer.start()
    while (true) {
      try {
        val result = client.getQueueUrl(GetQueueUrlRequest.builder().queueName("does not exist").build())
        result.join()
      } catch (e: Exception) {
        if (e is CompletionException && e.cause is QueueDoesNotExistException) {
          break
        }
        log.info { "sqs not available yet" }
        Thread.sleep(1000)
      }
    }
    log.info { "sqs is available" }
  }

  override fun shutdown() {
    composer.stop()
  }
}

fun main() {
  DockerSqs.startup()
}
