package misk.cluster.messaging.core

import com.google.common.util.concurrent.AbstractExecutionThreadService
import com.google.inject.Inject
import com.google.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import misk.cluster.messaging.core.dispatcher.ConnectClusterMember
import misk.cluster.messaging.core.dispatcher.DisconnectClusterMember
import misk.cluster.messaging.core.dispatcher.Dispatcher
import misk.cluster.messaging.core.dispatcher.SendClusterMessage
import misk.cluster.messaging.core.model.DeliverMessage
import misk.cluster.messaging.core.model.Topic
import misk.cluster.messaging.core.peer.PeerDiscoverer
import misk.cluster.messaging.core.peer.PeerNetwork
import okio.ByteString

@Singleton
@Suppress("UnstableApiUsage")
class ClusterMessagingService @Inject constructor(
  private val dispatcher: Dispatcher,
  private val peerDiscoverer: PeerDiscoverer,
  private val peerNetwork: PeerNetwork

) : AbstractExecutionThreadService(), Messenger {

//  private val actor = CoroutineScope().actor {
//
//  }

  override fun startUp() {
    runBlocking {

    }

    // TODO block until peer discover ers are connected

  }

  override fun run() {
    runBlocking {
      withContext(Dispatchers.Main) {
        // poll for actions

        //      messagingEngine.send()
      }
      withContext(Dispatchers.IO) {
        for (sideEffect in dispatcher.outbox) {

          when(sideEffect) {
            is ConnectClusterMember -> {
              peerNetwork.getOrCreate(sideEffect.recipient)
            }
            is DisconnectClusterMember -> {
              peerDiscoverer.disconnect(sideEffect.recipient)
            }
            is SendClusterMessage -> {
              peerDiscoverer.disconnect(sideEffect.recipient)
            }
          }
        }
      }
    }
    // poll for actions

    // TODO take peer factory?
    // connect engine

    TODO("not implemented")
  }

  override fun shutDown() {
    // TODO disconnect all peers
  }

  override fun publish(topic: Topic, message: ByteString): ReceiveChannel<ByteString> {


    // create a local peer?

    peerNetwork.getSelf().inbox.send(DeliverMessage(topic, message))
    TODO(
        "not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun subscribe(topic: Topic): ReceiveChannel<ByteString> {
    TODO(
        "not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}