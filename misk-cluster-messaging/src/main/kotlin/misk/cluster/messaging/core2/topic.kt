package misk.cluster.messaging.core2

import okio.ByteString
import kotlin.reflect.KClass

data class TopicRawMessageInboxAddress(
  val topicName: String
) : Address<ByteString> {
  override val stringValue: String
    get() = topicName
  override val messageType = ByteString::class
}

data class TopicRawMessageOutboxAddress(
  val topicName: String
) : Address<ByteString> {
  override val stringValue: String
    get() = topicName
  override val messageType = ByteString::class
}

// user facing
interface TopicMessageAddress<E: Any> : Address<E> {
  val topicName: String
}

data class TopicDecodedMessageInboxAddress<E : Any>(
  override val topicName: String,
  override val messageType: KClass<E>
) : TopicMessageAddress<E> {
  override val stringValue: String
    get() = topicName
}

data class TopicEncodedMessageOutboxAddress<E : Any>(
  override val topicName: String,
  override val messageType: KClass<E>
) : TopicMessageAddress<E> {
  override val stringValue: String
    get() = topicName
}

