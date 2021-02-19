package misk.eventrouter

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = false)
internal class EventRouterTest {
  @MiskTestModule
  val module = Modules.combine(
    MiskTestingServiceModule(),
    EventRouterTestingModule(distributed = true)
  )

  @Inject lateinit var machineA: RealEventRouter
  @Inject lateinit var machineB: RealEventRouter
  @Inject lateinit var machineC: RealEventRouter
  @Inject lateinit var clusterMapper: FakeClusterMapper
  @Inject lateinit var fakeEventRouterProcessor: EventRouterTester
  @Inject @field:ForEventRouterActions
  lateinit var actionExecutor: QueueingExecutorService

  @Test fun helloWorld() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    fakeEventRouterProcessor.processEverything()

    val machineAChatroom = machineA.getTopic<String>("chat")
    val machineAListener = RecordingListener()
    machineAChatroom.subscribe(machineAListener)

    val machineBChatroom = machineB.getTopic<String>("chat")
    machineBChatroom.publish("hello from Jesse!")

    fakeEventRouterProcessor.processEverything()

    assertThat(machineAListener.events).containsExactly("chat: open", "chat: hello from Jesse!")
  }

  @Test fun subscriberPublisherAndTopicAreThreeHosts() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2", "host_3"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    machineC.joinCluster()
    fakeEventRouterProcessor.processEverything()

    val listener = RecordingListener()
    machineB.getTopic<String>("chat").subscribe(listener)
    machineC.getTopic<String>("chat").publish("hello from Jesse!")

    fakeEventRouterProcessor.processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: hello from Jesse!")
  }

  @Test fun cancellingSubscription() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    fakeEventRouterProcessor.processEverything()

    val listener = RecordingListener()
    val subscription = machineA.getTopic<String>("chat").subscribe(listener)
    machineB.getTopic<String>("chat").publish("message 1")
    // Ensure that the message goes through before cancelling.
    fakeEventRouterProcessor.processEverything()

    subscription.cancel()
    machineB.getTopic<String>("chat").publish("message 2")
    fakeEventRouterProcessor.processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: message 1", "chat: close")
  }

  @Test fun leavingCluster() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    clusterMapper.setOwnerForHostList(listOf("host_2"), "host_2")
    machineA.joinCluster()
    machineB.joinCluster()
    fakeEventRouterProcessor.processEverything()

    val listener = RecordingListener()
    machineB.getTopic<String>("chat").subscribe(listener)
    // Ensure that we get an ack for our subscription
    fakeEventRouterProcessor.processEverything()
    machineA.leaveCluster()

    fakeEventRouterProcessor.processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: close")
  }

  @Test fun clusterChangeWithTopicOwnerRemainingTheSame() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    fakeEventRouterProcessor.processEverything()

    val listener = RecordingListener()
    machineA.getTopic<String>("chat").subscribe(listener)
    clusterMapper.setOwnerForHostList(listOf("host_1"), "host_1")
    machineB.leaveCluster()

    fakeEventRouterProcessor.processEverything()

    assertThat(listener.events).containsExactly("chat: open")
  }

  @Test fun publishAndSubscribeOnSameMachineThatIsNotTheTopicOwner() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    fakeEventRouterProcessor.processEverything()

    val listener = RecordingListener()
    machineB.getTopic<String>("chat").subscribe(listener)
    fakeEventRouterProcessor.processEverything()

    machineB.getTopic<String>("chat").publish("message 1")
    fakeEventRouterProcessor.processEverything()

    assertThat(listener.events).containsExactly("chat: open", "chat: message 1")
  }

  @Test fun singleMachine() {
    clusterMapper.setOwnerForHostList(listOf("host_1"), "host_1")
    machineA.joinCluster()
    fakeEventRouterProcessor.processEverything()

    val listener = RecordingListener()
    machineA.getTopic<String>("chat").subscribe(listener)
    fakeEventRouterProcessor.processEverything()

    machineA.getTopic<String>("chat").publish("message 1")
    fakeEventRouterProcessor.processEverything()

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
    fakeEventRouterProcessor.processEverything()

    val subscription = machineB.getTopic<String>("chat").subscribe(listener)
    fakeEventRouterProcessor.processEverything()

    machineA.getTopic<String>("chat").publish("message 1")
    fakeEventRouterProcessor.processEverything()

    subscription.cancel()
    fakeEventRouterProcessor.processEverything()

    assertThat(events).containsExactly("onOpen", "onEvent", "onClose")
  }

  @Test fun hostChange() {
    clusterMapper.setOwnerForHostList(listOf("host_1", "host_2", "host_3"), "host_1")
    machineA.joinCluster()
    machineB.joinCluster()
    machineC.joinCluster()
    fakeEventRouterProcessor.processEverything()

    val listenerA = RecordingListener()
    val listenerB = RecordingListener()
    val listenerC = RecordingListener()

    val topicA = machineA.getTopic<String>("chat")
    val topicB = machineB.getTopic<String>("chat")
    val topicC = machineC.getTopic<String>("chat")
    topicA.subscribe(listenerA)
    topicB.subscribe(listenerB)
    topicC.subscribe(listenerC)
    fakeEventRouterProcessor.processEverything()

    topicA.publish("a")
    topicB.publish("b")
    topicC.publish("c")
    fakeEventRouterProcessor.processEverything()

    clusterMapper.setOwnerForHostList(listOf("host_2", "host_3"), "host_2")
    machineA.leaveCluster()
    fakeEventRouterProcessor.processEverything()

    assertThat(listenerA.events).containsExactly(
      "chat: open", "chat: a", "chat: b", "chat: c",
      "chat: close"
    )
    assertThat(listenerB.events).containsExactly(
      "chat: open", "chat: a", "chat: b", "chat: c",
      "chat: close"
    )
    assertThat(listenerC.events).containsExactly(
      "chat: open", "chat: a", "chat: b", "chat: c",
      "chat: close"
    )

    topicB.subscribe(listenerB)
    topicC.subscribe(listenerC)
    fakeEventRouterProcessor.processEverything()

    topicB.publish("b2")

    fakeEventRouterProcessor.processEverything()

    assertThat(listenerA.events).containsExactly(
      "chat: open", "chat: a", "chat: b", "chat: c",
      "chat: close"
    )
    assertThat(listenerB.events).containsExactly(
      "chat: open", "chat: a", "chat: b", "chat: c",
      "chat: close", "chat: open", "chat: b2"
    )
    assertThat(listenerC.events).containsExactly(
      "chat: open", "chat: a", "chat: b", "chat: c",
      "chat: close", "chat: open", "chat: b2"
    )
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
