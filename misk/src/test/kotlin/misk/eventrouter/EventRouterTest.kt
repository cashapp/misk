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
  @Inject lateinit var clusterMapper: FakeClusterMapper
  @Inject @ForEventRouterActions lateinit var actionExecutor: QueueingExecutorService
  @Inject @ForEventRouterSubscribers lateinit var subscriberExecutor: QueueingExecutorService

  @Test fun helloWorld() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    processEverything()

    val machineAChatroom = machineA.getTopic<String>("chat")
    val machineAListener = RecordingListener()
    machineAChatroom.subscribe(machineAListener)

    val machineBChatroom = machineB.getTopic<String>("chat")
    machineBChatroom.publish("hello from Jesse!")

    processEverything()

    assertThat(machineAListener.events).containsExactly("chat: open", "chat: hello from Jesse!")
  }

  @Test fun subscriberPublisherAndTopicAreThreeHosts() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2", "host_3"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    machineC.joinCluster()
    processEverything()

    val listener = RecordingListener()
    machineB.getTopic<String>("chat").subscribe(listener)
    machineC.getTopic<String>("chat").publish("hello from Jesse!")

    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: hello from Jesse!")
  }

  @Test fun cancellingSubscription() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    processEverything()

    val listener = RecordingListener()
    val subscription = machineA.getTopic<String>("chat").subscribe(listener)
    machineB.getTopic<String>("chat").publish("message 1")
    // Ensure that the message goes through before cancelling.
    processEverything()

    subscription.cancel()
    machineB.getTopic<String>("chat").publish("message 2")
    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: message 1", "chat: close")
  }

  @Test fun leavingCluster() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    clusterMapper.setOwnerForHostList(listOf("host_2"), "host_2")
    machineA.joinCluster()
    machineB.joinCluster()
    processEverything()

    val listener = RecordingListener()
    machineB.getTopic<String>("chat").subscribe(listener)
    // Ensure that we get an ack for our subscription
    processEverything()
    machineA.leaveCluster()

    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: close")
  }

  @Test fun clusterChangeWithTopicOwnerRemainingTheSame() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    processEverything()

    val listener = RecordingListener()
    machineA.getTopic<String>("chat").subscribe(listener)
    clusterMapper.setOwnerForHostList(listOf("host_1"), "host_1")
    machineB.leaveCluster()

    processEverything()

    assertThat(listener.events).containsExactly("chat: open")
  }

  @Test fun publishAndSubscribeOnSameMachineThatIsNotTheTopicOwner() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    processEverything()

    val listener = RecordingListener()
    machineB.getTopic<String>("chat").subscribe(listener)
    processEverything()

    machineB.getTopic<String>("chat").publish("message 1")
    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: message 1")
  }

  @Test fun singleMachine() {
    clusterMapper.setOwnerForHostList(listOf("host_1"), "host_1")
    machineA.joinCluster()
    processEverything()

    val listener = RecordingListener()
    machineA.getTopic<String>("chat").subscribe(listener)
    processEverything()

    machineA.getTopic<String>("chat").publish("message 1")
    processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: message 1")
  }

  @Test fun listenerCallbacksAreNotOnTheRouterActionThread() {
    val events = mutableListOf<String>()
    val listener = object : Listener<String> {
      override fun onOpen(subscription: Subscription<String>) {
        assertThat(actionExecutor.isProcessing()).isFalse()
        events.add("onOpen")
      }

      override fun onEvent(subscription: Subscription<String>, event: String) {
        assertThat(actionExecutor.isProcessing()).isFalse()
        events.add("onEvent")
      }

      override fun onClose(subscription: Subscription<String>) {
        assertThat(actionExecutor.isProcessing()).isFalse()
        events.add("onClose")
      }
    }

    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
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

  @Test fun hostChange() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2", "host_3"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    machineC.joinCluster()
    processEverything()

    val listenerA = RecordingListener()
    val listenerB = RecordingListener()
    val listenerC = RecordingListener()

    val topicA = machineA.getTopic<String>("chat")
    val topicB = machineB.getTopic<String>("chat")
    val topicC = machineC.getTopic<String>("chat")
    topicA.subscribe(listenerA)
    topicB.subscribe(listenerB)
    topicC.subscribe(listenerC)
    processEverything()

    topicA.publish("a")
    topicB.publish("b")
    topicC.publish("c")
    processEverything()

    clusterMapper.setOwnerForHostList(listOf("host_2", "host_3"), "host_2")
    machineA.leaveCluster()
    processEverything()

    assertThat(listenerA.events).containsExactly("chat: open", "chat: a", "chat: b", "chat: c",
        "chat: close")
    assertThat(listenerB.events).containsExactly("chat: open", "chat: a", "chat: b", "chat: c",
        "chat: close")
    assertThat(listenerC.events).containsExactly("chat: open", "chat: a", "chat: b", "chat: c",
        "chat: close")

    topicB.subscribe(listenerB)
    topicC.subscribe(listenerC)
    processEverything()

    topicB.publish("b2")

    processEverything()

    assertThat(listenerA.events).containsExactly("chat: open", "chat: a", "chat: b", "chat: c",
        "chat: close")
    assertThat(listenerB.events).containsExactly("chat: open", "chat: a", "chat: b", "chat: c",
        "chat: close", "chat: open", "chat: b2")
    assertThat(listenerC.events).containsExactly("chat: open", "chat: a", "chat: b", "chat: c",
        "chat: close", "chat: open", "chat: b2")
  }

  private fun processEverything() {
    do {
      var total = 0
      total += clusterConnector.processEverything()
      total += actionExecutor.processEverything()
      total += subscriberExecutor.processEverything()
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
