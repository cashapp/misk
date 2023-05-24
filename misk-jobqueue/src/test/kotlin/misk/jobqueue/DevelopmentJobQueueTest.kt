package misk.jobqueue

import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import javax.inject.Inject

@MiskTest(startService = true)
internal class DevelopmentJobQueueTest {
  @MiskTestModule private val module = DevelopmentTestModule()

  @Inject private lateinit var exampleJobEnqueuer: ExampleJobEnqueuer
  @Inject private lateinit var logCollector: LogCollector

  @Test
  fun basic() {
    exampleJobEnqueuer.enqueueRed("stop sign")
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received RED job with message: stop sign"
    )
    exampleJobEnqueuer.enqueueGreen("dinosaur")
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: dinosaur"
    )
    exampleJobEnqueuer.enqueueGreen("android")
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received GREEN job with message: android"
    )
  }

  @Test
  fun batch() {
    exampleJobEnqueuer.batchEnqueueRed(listOf("stop sign", "apple"))
    assertThat(logCollector.takeMessages(ExampleJobHandler::class)).containsExactlyInAnyOrder(
      "received RED job with message: stop sign",
      "received RED job with message: apple"
    )
  }
}

private class DevelopmentTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(LogCollectorModule())
    install(FakeJobHandlerModule.create<ExampleJobHandler>(RED_QUEUE))
    install(FakeJobHandlerModule.create<ExampleJobHandler>(GREEN_QUEUE))
    install(DevelopmentJobProcessorModule())
    bind<JobQueue>().to<FakeJobQueue>()
  }
}
