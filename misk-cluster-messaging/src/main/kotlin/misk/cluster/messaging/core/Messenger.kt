package misk.cluster.messaging.core

import kotlinx.coroutines.channels.ReceiveChannel
import misk.cluster.messaging.core.model.Topic
import misk.cluster.messaging.core.peer.Peer
import okio.ByteString

interface Messenger {

  fun publish(topic: Topic, message: ByteString): ReceiveChannel<ByteString>

  fun subscribe(topic: Topic): ReceiveChannel<ByteString>
}

interface MessengerBuilder {

  fun discoverer() // cluster member discovery, client discovery, server discovery

  // virtual node share the same dispatcher?

  fun build()
}

interface Messenger2 {

  fun publish2(topic: Topic, message: ByteString): Peer // a local peer per topic??

  fun subscribe2(topic: Topic): Peer// local peer
}

// publish to a message to a topic returns a
