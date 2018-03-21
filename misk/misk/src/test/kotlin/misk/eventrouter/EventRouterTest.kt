package misk.eventrouter

import com.google.inject.util.Modules
import misk.moshi.MoshiModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
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
    // The topic owner.
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

  @Disabled("broken!")
  @Test fun leavingCluster() {
    val listener = RecordingListener()
    // Machine A is the topic owner
    machineA.joinCluster()
    machineB.joinCluster()

    // Ensure the machine B knows about A
    processEverything()

    machineB.getTopic<String>("chat").subscribe(listener)
    // Ensure that we get an ack for our subscription
    processEverything()
    machineA.leaveCluster()

    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: close")
  }

  @Test fun clusterChangeWithTopicOwnerRemainingTheSame() {
    val listener = RecordingListener()
    // Machine A is the topic owner
    machineA.joinCluster()
    machineB.joinCluster()

    machineA.getTopic<String>("chat").subscribe(listener)
    machineB.leaveCluster()

    processEverything()

    assertThat(listener.events).containsExactly("chat: open")
  }

  @Test fun publishAndSubscribeOnSameMachineThatIsNotTheTopicOwner() {
    val listener = RecordingListener()

    // Machine A is the topic owner
    machineA.joinCluster()
    machineB.joinCluster()
    processEverything()

    machineB.getTopic<String>("chat").subscribe(listener)
    processEverything()

    machineB.getTopic<String>("chat").publish("message 1")
    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: message 1")
  }

  @Disabled("broken!")
  @Test fun singleMachine() {
    val listener = RecordingListener()

    machineA.joinCluster()
    processEverything()

    machineA.getTopic<String>("chat").subscribe(listener)
    processEverything()

    machineA.getTopic<String>("chat").publish("message 1")
    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: message 1")
  }

  @Disabled("broken!")
  @Test fun listenerCallbacksAreNotOnTheRouterActionThread() {
    val events = mutableListOf<String>()
    val listener = object: Listener<String> {
      override fun onOpen(subscription: Subscription<String>) {
        assertThat(executorService.isProcessing()).isFalse()
        events.add("onOpen")
      }

      override fun onEvent(subscription: Subscription<String>, event: String) {
        assertThat(executorService.isProcessing()).isFalse()
        events.add("onEvent")
      }

      override fun onClose(subscription: Subscription<String>) {
        assertThat(executorService.isProcessing()).isFalse()
        events.add("onClose")
      }
    }

    machineA.joinCluster()
    machineB.joinCluster()
    processEverything()

    val subscription = machineB.getTopic<String>("chat").subscribe(listener)
    processEverything()

    machineA.getTopic<String>("chat").publish("message 1")
    processEverything()

    subscription.cancel()
    processEverything()

    assertThat(events).containsExactly("onOpen", "onEvent", "onClose")
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
