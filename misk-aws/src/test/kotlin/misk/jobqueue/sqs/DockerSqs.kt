package misk.jobqueue.sqs

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.QueueDoesNotExistException
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Ports
import misk.containers.Composer
import misk.containers.Container
import misk.containers.ContainerUtil
import misk.jobqueue.sqs.DockerSqs.clientPort
import misk.logging.getLogger
import misk.testing.ExternalDependency

/** A test SQS Service. Tests can connect to the service at 127.0.0.1:[clientPort] */
internal object DockerSqs : ExternalDependency {

  private val log = getLogger<DockerSqs>()
  private const val clientPort = 9324
  private const val hostInternalTarget = "host.docker.internal"

  override fun beforeEach() {
    // noop
  }

  /** cleans up the queues after each run */
  override fun afterEach() {
    val queues = client.listQueues()
    queues.queueUrls.forEach { client.deleteQueue(ensureUrlWithProperTarget(it)) }
  }

  private val composer =
    Composer(
      "e-sqs",
      Container {
        // NB(mmihic): Because the client port is embedded directly into the queue URLs, we have to use
        // the same external port as we do for the internal port
        val exposedClientPort = ExposedPort.tcp(clientPort)
        withImage("softwaremill/elasticmq:1.6.5")
          .withName("sqs")
          .withExposedPorts(exposedClientPort)
          .withHostConfig(
            HostConfig.newHostConfig()
              .withPortBindings(Ports().apply { bind(exposedClientPort, Ports.Binding.bindPort(clientPort)) })
          )
      },
    )

  val credentials =
    object : AWSCredentialsProvider {
      override fun refresh() {}

      override fun getCredentials(): AWSCredentials {
        return BasicAWSCredentials("access-key-id", "secret-access-key")
      }
    }

  private fun ensureUrlWithProperTarget(url: String): String {
    return if (ContainerUtil.isRunningInDocker)
      url.replace("localhost", hostInternalTarget).replace("127.0.0.1", hostInternalTarget)
    else url
  }

  val endpoint =
    AwsClientBuilder.EndpointConfiguration("http://${ContainerUtil.dockerTargetOrLocalIp()}:$clientPort", "us-east-1")

  val client: AmazonSQS =
    AmazonSQSClient.builder().withCredentials(credentials).withEndpointConfiguration(endpoint).build()

  override fun startup() {
    composer.start()
    while (true) {
      try {
        client.getQueueUrl("does not exist")
      } catch (e: Exception) {
        if (e is QueueDoesNotExistException) {
          break
        }
        log.info { "sqs not available yet" }
        Thread.sleep(100)
      }
    }
    log.info { "sqs is available" }
  }

  override fun shutdown() {
    composer.stop()
  }
}

fun main(args: Array<String>) {
  DockerSqs.startup()
}
