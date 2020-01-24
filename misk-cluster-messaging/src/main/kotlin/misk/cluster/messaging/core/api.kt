package misk.cluster.messaging.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import okio.ByteString

interface Mailbox {
  fun open(): Pair<SendChannel<ByteString>, ReceiveChannel<ByteString>>

  fun close()
}



interface Mailroom {

  fun openMailbox(mailboxScope: CoroutineScope, topic: String): Mailbox

}

suspend fun CoroutineScope.openMailbox(mailroom: Mailroom, topic: String) : Mailbox {
  return mailroom.openMailbox(this, topic)
}


