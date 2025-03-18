package misk.vitess.testing.internal

class VitessClusterConfig(port: Int) {
  val vtgatePort: Int = port
  val mysqlPort: Int = port - 1
  val grpcPort: Int = port - 2
  val basePort: Int = port - 3
  val hostname: String = System.getenv("VITESS_HOST") ?: "localhost"
  val vtgateUser: String = "root"
  val vtgateUserPassword: String = ""
  val dbaUser: String = "vt_dba_tcp_full"
  val dbaUserPassword: String = ""

  fun allPorts(): List<Int> = listOf(basePort, grpcPort, mysqlPort, vtgatePort)
}
