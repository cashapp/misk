package misk.vitess

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.netty.NegotiationType
import io.grpc.netty.NettyChannelBuilder
import io.vitess.proto.Query
import io.vitess.proto.Topodata
import io.vitess.proto.Vtgate
import io.vitess.proto.grpc.VitessGrpc
import misk.jdbc.DataSourceConfig
import mu.KotlinLogging
import java.time.Instant

data class Update(
  val statements: List<String>,
  /**
   * The position to restart from.
   */
  val position: String
)

class UpdateStreamSource(
  private val config: DataSourceConfig
) {
  private fun target() = "${host()}:${port()}"

  private fun host() = config.host ?: "127.0.0.1"

  private fun port() = config.port ?: 27001

  fun openGrpcClient(): VitessGrpc.VitessStub {
    return VitessGrpc.newStub(channel()).withWaitForReady()
  }

  fun openGrpcFutureClient(): VitessGrpc.VitessFutureStub {
    return VitessGrpc.newFutureStub(channel()).withWaitForReady()
  }

  private fun channel(): ManagedChannel {
    // TODO Doesn't support TLS!!!
    return NettyChannelBuilder.forTarget(target())
        .negotiationType(NegotiationType.PLAINTEXT)
        .build()
  }

  /**
   * Start streaming updates from specified shard. If neither position nor timestamp is specified
   * we will stream from the start of the binlogs.
   *
   * @param shard which shard to stream updates from.
   * @param position the GTID position to start from.
   * @param timestamp if no GTID position then start streaming from specified timestamp.
   */
  fun streamUpdates(
    shard: Shard,
    position: String?,
    timestamp: Instant?,
    listener: (Update) -> Unit
  ): Subscription {
    val subscription = Subscription(channel(), shard, position, timestamp, listener)
    subscription.start()
    return subscription
  }

  @Suppress("UNUSED_PARAMETER")
  fun streamUpdates(
    shard: Shard,
    fromPosition: String?,
    fromTime: Instant?
  ): Sequence<Update> {
    // TODO What happens if we loose the connection. Do we get an exception?
    // TODO We're not using the fromPosition yet...
    // TODO We're not using the fromTime yet...

    val request = Vtgate.UpdateStreamRequest.newBuilder()
        .setKeyspace(shard.keyspace.name)
        .setShard(shard.name)
        // TODO this is not right...
        .setTimestamp(0)
        .setTabletType(Topodata.TabletType.MASTER)

    return VitessGrpc.newBlockingStub(channel()).updateStream(request.build())
        .asSequence()
        .map { message ->
          Update(
              message.event.statementsList.map { it.sql.toStringUtf8() },
              message.event.eventToken.position)
        }
  }

  class Subscription(
    val channel: ManagedChannel,
    val shard: Shard,
    val fromPosition: String?,
    val fromTime: Instant?,
    val listener: (Update) -> Unit
  ) : ClientCall.Listener<Vtgate.UpdateStreamResponse?>() {
    var isReady = false
      private set
    var isStarted = false
      private set
    var isClosed = false
      private set
    var closedStatus: Status? = null
      private set

    private var call: ClientCall<Vtgate.UpdateStreamRequest, Vtgate.UpdateStreamResponse> =
        channel.newCall(VitessGrpc.getUpdateStreamMethod(),
            CallOptions.DEFAULT.withWaitForReady())

    override fun onMessage(message: Vtgate.UpdateStreamResponse?) {
      if (message != null) {
        listener(Update(
            message.event.statementsList.map { it.sql.toStringUtf8() },
            message.event.eventToken.position))
      }
      call.request(1)
    }

    override fun onReady() {
      isReady = true;
    }

    override fun onClose(status: Status?, trailers: Metadata?) {
      logger.warn("UpdateStream closed: $status")
      isClosed = true
      closedStatus = status;
    }

    internal fun start() {
      // TODO This doesn't support reconnecting if e.g. the vtgate goes down for a redeploy
      // TODO We're not using the fromPosition yet...
      // TODO We're not using the fromTime yet...
      call.start(this, Metadata())
      call.request(1)

      val request = Vtgate.UpdateStreamRequest.newBuilder()
          .setKeyspace(shard.keyspace.name)
          .setShard(shard.name)
          .setTabletType(Topodata.TabletType.MASTER)

      if (fromTime != null) {
        request.timestamp = fromTime.toEpochMilli() / 1000
      }
      if (fromPosition != null) {
        request.event = Query.EventToken.newBuilder()
            .setShard(shard.name)
            .setPosition(fromPosition)
            .build()
      }

      call.sendMessage(request.build())
      call.halfClose()

      isStarted = true
    }

    fun cancel() {
      call.cancel("Cancel because we're done", null)
    }
  }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
