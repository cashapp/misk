package misk.cluster.messaging.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking
import okio.ByteString
import java.io.Closeable

//data class Mailbox(
//  val sendChannel: SendChannel<ByteString>,
//  val receiveChannel: ReceiveChannel<ByteString>
//) : Closeable {
//  override fun close() {
//    sendChannel.close()
//    receiveChannel.cancel()
//  }
//}



interface Mailroom {

  fun openSendChannel(mailboxScope: CoroutineScope, topic: String): SendChannel<Any>
  fun openReceiveChannel(mailboxScope: CoroutineScope, topic: String): ReceiveChannel<Any>

}

suspend fun CoroutineScope.openSendChannel(mailroom: Mailroom, topic: String) : SendChannel<Any> {
  return mailroom.openSendChannel(this, topic)
}

suspend fun CoroutineScope.openReceiveChannel(mailroom: Mailroom, topic: String) : ReceiveChannel<Any> {
  return mailroom.openReceiveChannel(this, topic)
}


// example

lateinit var mailroom: Mailroom
fun writeSyncEntity(topic: String, entityId: String) {

  // write

  runBlocking {
    val sendChannel = openSendChannel(mailroom, topic)
    val receiveChannel = openReceiveChannel(mailroom, topic) {
      // invoked when subscription is confirmed?
    }

    sendChannel.send(entityId.encodeUtf8())
    sendChannel.close()

    assertThat(receiveChannel.receive()).isEqualTo(entityId);


  }

  // B
  runBlocking {
    val sendChannel = openSendChannel(mailroom, "A's request topic")
    sendChannel.send("send me updates")
    val receiveChannel = openReceiveChannel(mailroom, "B's request topic")

  }

}

/*
Device A connects on topic A as subscriber
Device B connects on topic A as subscriber

A
publish event 1 on topic A
publish event 2 on topic A
publish event 3 on topic A
publish event 4 on topic A

 - does B immediately receive event 1?
publish event 4 5on topic A


 */

fun handleAwatiRequest() {

}

