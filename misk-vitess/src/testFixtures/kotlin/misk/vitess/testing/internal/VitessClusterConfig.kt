package misk.vitess.testing.internal

import misk.vitess.testing.DefaultSettings
import misk.vitess.testing.DefaultSettings.CONTAINER_PORT_BASE
import misk.vitess.testing.DefaultSettings.CONTAINER_PORT_GRPC
import misk.vitess.testing.DefaultSettings.CONTAINER_PORT_MYSQL
import misk.vitess.testing.DefaultSettings.CONTAINER_PORT_VTGATE
import wisp.containers.ContainerUtil
import java.net.ServerSocket

/**
 * This class represents the Vitess ports to be used by Vitess Docker components.
 */
class VitessClusterConfig private constructor(
  val vtgatePort: VitessPortMapping,
  val mysqlPort: VitessPortMapping,
  val grpcPort: VitessPortMapping,
  val basePort: VitessPortMapping,
  val hostname: String,
  val vtgateUser: String,
  val vtgateUserPassword: String,
  val dbaUser: String,
  val dbaUserPassword: String

) {
  companion object {
    /**
     * Creates a [VitessClusterConfig] using the specified user port.
     *
     * The `userPort` arg represents the user desired location of where the vtagte lives.
     * If the user port is set to [DefaultSettings.DYNAMIC_PORT], a dynamic port will instead be assigned.
     * All other Vitess ports are dynamic. Dynamic port mode is generally preferred to avoid port conflicts.
     *
     * @param userPort The port to expose for vtgate. Use [DefaultSettings.DYNAMIC_PORT] for dynamic assignment.
     *
     * @return A new instance of [VitessClusterConfig].
     */
    fun create(userPort: Int): VitessClusterConfig {
      fun getDynamicPort() = ServerSocket(0).use { it.localPort }
      val vtgateExposedPort = if (userPort == DefaultSettings.DYNAMIC_PORT) getDynamicPort() else userPort
      return VitessClusterConfig(
        vtgatePort = VitessPortMapping(hostPort = vtgateExposedPort, containerPort = CONTAINER_PORT_VTGATE),
        mysqlPort = VitessPortMapping(hostPort = getDynamicPort(), containerPort = CONTAINER_PORT_MYSQL),
        grpcPort = VitessPortMapping(hostPort = getDynamicPort(), containerPort = CONTAINER_PORT_GRPC),
        basePort = VitessPortMapping(hostPort = getDynamicPort(), containerPort = CONTAINER_PORT_BASE),
        hostname = System.getenv("VITESS_HOST") ?: ContainerUtil.dockerTargetOrLocalHost(),
        vtgateUser = "root",
        vtgateUserPassword = "",
        dbaUser = "vt_dba_tcp_full",
        dbaUserPassword = ""
      )
    }
  }

  fun allPortMappings(): List<VitessPortMapping> = listOf(basePort, grpcPort, mysqlPort, vtgatePort)

  fun allHostPorts(): List<Int> = allPortMappings().map { it.hostPort }
}

data class VitessPortMapping(
  val containerPort: Int,
  val hostPort: Int
)

