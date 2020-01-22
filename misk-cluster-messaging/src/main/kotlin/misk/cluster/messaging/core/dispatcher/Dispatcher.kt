package misk.cluster.messaging.core.dispatcher

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import misk.cluster.messaging.core.dispatcher.draft.Client
import misk.cluster.messaging.core.dispatcher.draft.ClientDispatcher
import misk.cluster.messaging.core.dispatcher.draft.ClusterDispatcher
import misk.cluster.messaging.core.model.ClientMessage
import misk.cluster.messaging.core.model.ClusterMessage
import misk.cluster.messaging.core.model.Topic
import okio.ByteString
import java.util.concurrent.ConcurrentHashMap

interface MessageRouter {
  val clientDispatcher: ClientDispatcher

  fun publish(topic: Topic, content: ByteString) {
    runBlocking {
      clientDispatcher.get(topic).inbox.send(content)
    }
  }

  fun subscribe(topic: Topic): ReceiveChannel<ByteString> {
    return runBlocking {
      clientDispatcher.get(topic).outbox
    }
  }

}

interface ClusterDispatcher {

  // subscribes to cluster member messages and writes to clients

  // read only api
}

interface ClusterInterestGraph {

  // subscribes to cluster members

  // exposes read only api
}

interface ClusterTopology {

  // subscribes to cluster broadcast
  // subscribes to k8s watcher
  // maintains connections to cluster members

  // read only api
}

class ClientDispatcher {
  private val clients = ConcurrentHashMap<Topic, Client>()

  suspend fun get(topic: Topic): Client {
    return clients.getOrPut(topic) { Client() }
  }

  // subscribes to client messages and writes to cluster

  suspend fun publish(topic: Topic, content: ByteString)
  // is any peer interested in this topic?

  suspend fun subscribe(topic: Topic): ReceiveChannel<ByteString>
  // creates a subscription object
  // if the channel closes
}

interface ClientInterestGraph {

  // subscribes to clients

  // exposes read only api
}

interface ClusterMember {

  // set desired state

  // lifecycle event ->

  val inbox: SendChannel<ClusterMessage> // if fail to write, skip the msg
  val outbox: ReceiveChannel<ClusterMessage>

  fun join()
  fun leave()
}

// topic client?

class Client {

//  val inbox: SendChannel<ByteString>
//  val outbox: ReceiveChannel<ByteString>

  private val inbox: Channel<ClientMessage> = Channel() // should block
  private val outbox: Channel<ClientMessage> = Channel(5)
  private var forwardJob: Job? = null

  private lateinit var clusterDispatcher: ClusterDispatcher

  // lifecycle methods
  fun join() {
    forwardJob = GlobalScope.launch {
      for (message in inbox) {

      }
    }
  }

  fun leave() {
    forwardJob?.cancel()
    forwardJob = null
  }
}
