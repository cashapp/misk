package misk.cluster.messaging.core2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.ByteString

class TopicMessageCodecOperator(
  private val coroutineScope: CoroutineScope,
  private val mailboxRegistry: MailboxRegistry
) {

  init {
    coroutineScope.launch {
      // we want to subscribe to a type of address and
      val mailboxUpdate = mailboxRegistry.getMailbox(MailboxUpdateAddress)
          .transport
          .openSubscription()
      for (mailbox in mailboxUpdate) {
        if (mailbox.message !is MailboxUpdate.Created) {
          continue
        }
        // subscribe this address
        if (mailbox.message.address is TopicDecodedMessageInboxAddress) {
          launch {
            startDecoding(mailbox.message.address as TopicDecodedMessageInboxAddress<Any>)
          }
        }
        if (mailbox.message.address is TopicEncodedMessageOutboxAddress) {
          launch {
            startEncoding(mailbox.message.address as TopicEncodedMessageOutboxAddress<Any>)
          }
        }
      }
    }
  }

  private suspend fun startDecoding(toAddress: TopicDecodedMessageInboxAddress<Any>) {
    val fromAddress = TopicRawMessageInboxAddress(toAddress.topicName)
    for (rawMessage in mailboxRegistry.getMailbox(fromAddress).transport.openSubscription()) {
      mailboxRegistry.getMailbox(toAddress).send(decode(rawMessage))
    }
  }

  private suspend fun startEncoding(toAddress: TopicEncodedMessageOutboxAddress<Any>) {
    val fromAddress = TopicRawMessageOutboxAddress(toAddress.topicName)
    for (rawMessage in mailboxRegistry.getMailbox(fromAddress).transport.openSubscription()) {
      mailboxRegistry.getMailbox(toAddress).send(encode(rawMessage))
    }
  }

  private fun decode(rawMessage: Envelope<TopicRawMessageInboxAddress, ByteString>): Any {
    TODO()
  }

  private fun encode(message: Any): Envelope<TopicRawMessageInboxAddress, ByteString> {
    TODO()
  }

}

// encoder subscribes to raw inbox and writes to decoded inbox
