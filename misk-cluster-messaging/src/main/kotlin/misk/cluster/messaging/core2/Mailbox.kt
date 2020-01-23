package misk.cluster.messaging.core2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class Mailroom {

  lateinit var mailboxRegistry: MailboxRegistry

}

// could this be a temporary operator?
class MailboxSession<A: Address<M>, M: Any>(
  private val coroutineScope: CoroutineScope,
  private val mailbox: Mailbox<A, M>
) {

  private val receiveChannel = coroutineScope.produce {
    for (message in mailbox.transport.openSubscription()) {
      send(message.message)
    }
  }

  suspend fun receive(): M {
    return receiveChannel.receive()
  }

  fun consumeAsFlow(): Flow<M> {
    return receiveChannel.consumeAsFlow()
  }

  suspend fun send(message: M) {
    mailbox.send(message)
  }

}


// top level methods should be suspend functions to avoid leaks
suspend fun <E: Any> CoroutineScope.receive(mailroom: Mailroom, topic: String, type: KClass<E>): E {
  val address = TopicDecodedMessageInboxAddress(topic, type)
  return openMailbox(mailroom, address).receive()
}

suspend inline fun <reified E: Any> CoroutineScope.receive(mailroom: Mailroom, topic: String): E {
  return receive(mailroom, topic, E::class)
}

suspend fun <E: Any> CoroutineScope.send(mailroom: Mailroom, topic: String, message: E, type: KClass<E>) {
  val address = TopicEncodedMessageOutboxAddress(topic, type)
  return openMailbox(mailroom, address).send(message)
}

suspend inline fun <reified E: Any> CoroutineScope.send(mailroom: Mailroom, topic: String, message: E) {
  return send(mailroom, topic, message, E::class)
}

suspend fun <A : Address<M>, M : Any> CoroutineScope.openMailbox(mailroom: Mailroom, address: A) : MailboxSession<A, M> {
  // transport should be shared and bounded to mailroom scope, mailbox can be unique and bound to user scope...
  return MailboxSession(
      this,
      mailroom.mailboxRegistry.getMailbox(address)
  )
}


// example

// C_123 -> SyncEntityUpdate...
data class SyncEntityUpdate(val syncEntityId: String)

class AwaitTopicWebAction {

  private lateinit var mailroom: Mailroom

  fun execute(request: Request) {
    runBlocking {
      val update: SyncEntityUpdate = receive(mailroom, request.topic)
    }
  }

  data class Request(val topic: String)
}