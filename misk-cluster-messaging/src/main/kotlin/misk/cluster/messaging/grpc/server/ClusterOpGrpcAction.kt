package misk.cluster.messaging.grpc.server

import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import clustermessaging.ClusterMessagingClusterOpServer
import clustermessaging.Op
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import misk.cluster.messaging.core.ClusterMember
import misk.cluster.messaging.core.ClusterMemberNetwork
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClusterOpGrpcAction @Inject constructor(
  private val clusterMemberNetwork: ClusterMemberNetwork,
  private val clusterMemberFactory: ClusterMember.Factory
) : WebAction, ClusterMessagingClusterOpServer {

  @LogRequestResponse(sampling = 1.0, includeBody = true)
  override suspend fun ClusterOp(request: SendChannel<Op>, response: ReceiveChannel<Op>) {
    clusterMemberNetwork.registerRemoteClusterMember(
        clusterMemberFactory.create("todo: host", response, request)
    )
  }

}
