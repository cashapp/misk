package misk.vitess

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.netty.NegotiationType
import io.grpc.netty.NettyChannelBuilder
import io.vitess.proto.Query
import io.vitess.proto.Topodata
import io.vitess.proto.Vtgate
import io.vitess.proto.grpc.VitessGrpc
import misk.jdbc.DataSourceConfig

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
    timestamp: Long?,
    listener: (Update) -> Any
  ): Subscription {
    val subscription = Subscription(channel(), shard, position, timestamp, listener)
    subscription.start()
    return subscription
  }

  class Subscription(
    val channel: ManagedChannel,
    val shard: Shard,
    val position: String?,
    val timestamp: Long?,
    val listener: (Update) -> Any
  ) : ClientCall.Listener<Vtgate.UpdateStreamResponse?>() {
    private var call: ClientCall<Vtgate.UpdateStreamRequest, Vtgate.UpdateStreamResponse>? = null

    override fun onMessage(message: Vtgate.UpdateStreamResponse?) {
      if (message != null) {
        listener(Update(
            message.event.statementsList.map { it.sql.toStringUtf8() },
            message.event.eventToken.position))
      }
    }

    internal fun start() {
      // TODO This doesn't support reconnecting if e.g. the vtgate goes down for a redepl
      val call = channel.newCall(VitessGrpc.getUpdateStreamMethod(),
          CallOptions.DEFAULT.withWaitForReady())
      this.call = call
      call.start(this, Metadata())

      val eventToken = Query.EventToken.newBuilder()
          .setShard(shard.name)
      if (position != null) {
        eventToken.position = position
      }
      if (timestamp != null) {
        eventToken.timestamp = timestamp
      }
      call.sendMessage(Vtgate.UpdateStreamRequest.newBuilder()
          .setKeyspace(shard.keyspace.name)
          .setShard(shard.name)
          .setTabletType(Topodata.TabletType.REPLICA)
//        .setEvent(eventToken.build())
          .build())
      call.halfClose()
    }

    fun cancel() {
      val call = this.call
      this.call = null
      call?.cancel(null, null)
    }
  }

  interface Listener {
    fun update(it: Vtgate.UpdateStreamResponse)
  }
}
