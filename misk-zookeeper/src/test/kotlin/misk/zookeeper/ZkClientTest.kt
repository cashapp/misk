package misk.zookeeper

import com.google.common.collect.Iterables
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.config.AppNameModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.zookeeper.testing.ZkTestModule
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.ZooDefs
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.data.Id
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier

@MiskTest
internal class ZkClientTest {
  @MiskTestModule private val module = Modules.combine(
      MiskTestingServiceModule(),
      AppNameModule("my-app"),
      ZkTestModule(Zk1::class),
      ZkTestModule(Zk2::class))

  @Inject @Zk1 lateinit var clientFactory1: ZkClientFactory
  @Inject @Zk1 lateinit var curatorFramework1: CuratorFramework

  @Inject @Zk2 lateinit var clientFactory2: ZkClientFactory
  @Inject @Zk2 lateinit var curatorFramework2: CuratorFramework

  @Test fun clientDefaultsAreCorrect() {
    val client = clientFactory1.client()
    client.create().forPath("/test-node", "test-data".toByteArray())

    // Data is written to /services/<app-name>/data by default
    val data = curatorFramework1.data.forPath("/services/my-app/data/test-node")
    assertThat(String(data)).isEqualTo("test-data")

    // Data has correct ACL set
    val dataAcl = curatorFramework1.acl.forPath("/services/my-app/data/test-node")
    val dnFromCert = "CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US"
    val defaultACL = ACL(DEFAULT_PERMS, Id("x509", dnFromCert))
    assertThat(Iterables.getOnlyElement(dataAcl)).isEqualTo(defaultACL)

    // Data dir has correct ACL
    val dataDirAcl = curatorFramework1.acl.forPath("/services/my-app/data")
    assertThat(Iterables.getOnlyElement(dataDirAcl)).isEqualTo(defaultACL)

    // App dir has correct ACL
    val appDirAcl = curatorFramework1.acl.forPath("/services/my-app")
    assertThat(Iterables.getOnlyElement(appDirAcl)).isEqualTo(defaultACL)

    // Shared directory has correct ACL set
    val servicesAcl = curatorFramework1.acl.forPath("/services")
    assertThat(Iterables.getOnlyElement(servicesAcl))
        .isEqualTo(ACL(SHARED_DIR_PERMS, ZooDefs.Ids.ANYONE_ID_UNSAFE))
  }

  @Test fun multipleZks() {
    val client1 = clientFactory1.client()
    client1.create().forPath("/test-node-1", "test-data".toByteArray())

    val client2 = clientFactory2.client()
    client2.create().forPath("/test-node-2", "test-data".toByteArray())

    val data1 = curatorFramework1.data.forPath("/services/my-app/data/test-node-1")
    assertThat(String(data1)).isEqualTo("test-data")

    val data2 = curatorFramework2.data.forPath("/services/my-app/data/test-node-2")
    assertThat(String(data2)).isEqualTo("test-data")
  }

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
  annotation class Zk1

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
  annotation class Zk2
}
