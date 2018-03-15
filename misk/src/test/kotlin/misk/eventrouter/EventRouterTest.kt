package misk.eventrouter

import com.google.inject.util.Modules
import misk.moshi.MoshiModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = false)
internal class EventRouterTest {
  @MiskTestModule
  val module = Modules.combine(
      EventRouterTestingModule(),
      MoshiModule()
  )

  @Inject lateinit var machineA: RealEventRouter
  @Inject lateinit var machineB: RealEventRouter
  @Inject lateinit var machineC: RealEventRouter
  @Inject lateinit var clusterConnector: FakeClusterConnector
  @Inject lateinit var executorService: QueueingExecutorService

  @Test fun helloWorld() {
    machineA.joinCluster()
    val machineAChatroom = machineA.getTopic<String>("chat")
    val machineAListener = RecordingListener()
    machineAChatroom.subscribe(machineAListener)

    machineB.joinCluster()
    val machineBChatroom = machineB.getTopic<String>("chat")
    machineBChatroom.publish("hello from Jesse!")

    processEverything()

    assertThat(machineAListener.events).containsExactly("chat: open", "chat: hello from Jesse!")
  }

  @Test fun subscriberPublisherAndTopicAreThreeHosts() {
    // The topic owner. (Currently hardcoded to the first machine to join the cluster.)
    machineA.joinCluster()

    // The subscriber.
    machineB.joinCluster()

    // The publisher.
    machineC.joinCluster()

    val listener = RecordingListener()
    machineB.getTopic<String>("chat").subscribe(listener)
    machineC.getTopic<String>("chat").publish("hello from Jesse!")

    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: hello from Jesse!")
  }

  @Test fun cancellingSubscription() {
    val listener = RecordingListener()
    machineA.joinCluster()
    machineB.joinCluster()

    val subscription = machineA.getTopic<String>("chat").subscribe(listener)
    machineB.getTopic<String>("chat").publish("message 1")
    // Ensure that the message goes through before cancelling.
    processEverything()

    subscription.cancel()
    machineB.getTopic<String>("chat").publish("message 2")
    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: message 1", "chat: close")
  }

  private fun processEverything() {
    do {
      var total = 0
      total += clusterConnector.processEverything()
      total += executorService.processEverything()
    } while (total > 0)
  }

  class RecordingListener : Listener<String> {
    val events = mutableListOf<String>()

    override fun onOpen(subscription: Subscription<String>) {
      events.add("${subscription.topic.name}: open")
    }

    override fun onEvent(subscription: Subscription<String>, event: String) {
      events.add("${subscription.topic.name}: $event")
    }

    override fun onClose(subscription: Subscription<String>) {
      events.add("${subscription.topic.name}: close")
    }
  }
}

