package misk.cluster.messaging.core2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import misk.cluster.messaging.MessageQueue
import okio.ByteString
import kotlin.reflect.KClass

// location transparency. this could be coming from a remote source
interface Address<M : Any> {
  val stringValue: String
  val messageType: KClass<M>
}

data class Envelope<A : Address<M>, M : Any>(
  val address: A,
  val message: M
    // sequence number, ... reply address
)

// when a mailbox is closed, subscribes should know?
class Mailbox<A : Address<M>, M : Any>(
  val address: A,
  val transport: BroadcastChannel<Envelope<A, M>>
    // created at, ...
) {

  suspend fun send(message: M) {
    // close the mailbox if the consume is behind
    transport.send(Envelope(address, message))
  }
}



interface Operator {

  // read from address, write to address

}



sealed class MailboxUpdate {

  data class Created(val address: Address<*>) : MailboxUpdate()
  data class Evicted(val address: Address<*>) : MailboxUpdate()
}

object MailboxUpdateAddress : Address<MailboxUpdate> {
  override val stringValue = "__mailbox_update"
  override val messageType = MailboxUpdate::class
}

/**
 * Creates and recycles mailboxes
 *
 * TODO could this be mailroom?
 */
class MailboxRegistry(
  private val coroutineScope: CoroutineScope
) {

  // TODO recycle mailbox dyanmically
  private val mailboxes = mutableMapOf<Address<*>, Mailbox<*, *>>()

  init {
    mailboxes[MailboxUpdateAddress] = Mailbox(MailboxUpdateAddress,
        Channel<Envelope<MailboxUpdateAddress, MailboxUpdate>>().broadcast())
  }

  suspend fun <A : Address<M>, M : Any> getMailbox(address: A): Mailbox<A, M> {
    return withContext(registryContext) {
      if (mailboxes.contains(address)) {
        mailboxes[address] as Mailbox<A, M>
      } else {
        createMailbox(address)
      }
    }
  }

  private suspend fun <A : Address<M>, M : Any> createMailbox(address: A): Mailbox<A, M> {
    val transport = (Channel<Any>() as Channel<Envelope<A, M>>).broadcast()
    val mailbox = Mailbox(address, transport)
    mailboxes[address] = mailbox
    getMailbox(MailboxUpdateAddress).send(MailboxUpdate.Created(address))
    return mailbox
  }

  suspend fun <A : Address<M>, M : Any> fanOut(addressType: KClass<A>, message: M) {
    for (address in mailboxes.keys) {
      if (addressType.isInstance(address)) {
        val mailbox = getMailbox(address as A)
        mailbox.send(message)
      }
    }
  }

  suspend fun <A : Address<M>, M : Any> fanIn(addressType: KClass<A>) : ReceiveChannel<Envelope<A, M>> {
    return coroutineScope.produce {
      val mailboxUpdate = getMailbox(MailboxUpdateAddress)
          .transport
          .openSubscription()
      for (update in mailboxUpdate) {
        when (update.message) {
          is MailboxUpdate.Created -> {
            if (addressType.isInstance(update.message.address)) {
              val mailbox = getMailbox(update.message.address as A)
              for (m in mailbox.transport.openSubscription()) {
                send(m)
              }
            }
          }
        }
      }
    }
  }


  companion object {
    private val registryContext = newSingleThreadContext("Mailbox Registry Context")
  }
}



// Operator


