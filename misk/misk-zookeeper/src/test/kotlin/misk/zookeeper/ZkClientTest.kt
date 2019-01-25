package misk.zookeeper

import com.google.common.collect.Iterables
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.clustering.zookeeper.ZkTestModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.apache.curator.framework.AuthInfo
import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.ZooDefs
import org.apache.zookeeper.ZooDefs.Ids.AUTH_IDS
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.data.Id
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.AuthProvider
import javax.inject.Inject
import kotlin.test.assertEquals

@MiskTest(startService = true)
internal class ZkClientTest {
  @MiskTestModule private val module = Modules.combine(MiskTestingServiceModule(), ZkTestModule())

  @Inject lateinit var clientFactory: ZkClientFactory
  @Inject lateinit var curatorFramework: CuratorFramework

  @Test fun clientDefaultsAreCorrect() {
    val client = clientFactory.client()
    client.create().forPath("/test-node", "test-data".toByteArray())

    // Data is written to /services/<app-name> by default
    val data = curatorFramework.data.forPath("/services/my-app/test-node")
    assertThat(String(data)).isEqualTo("test-data")

    // Data has correct ACL set
    val acl = curatorFramework.acl.forPath("/services/my-app/test-node")
    val dnFromCert = "CN=misk-client,OU=Client,O=Misk,L=San Francisco,ST=CA,C=US"
    assertThat(Iterables.getOnlyElement(acl)).isEqualTo(ACL(DEFAULT_PERMS, Id("x509", dnFromCert)))

    // Shared directory has correct ACL set
    val servicesAcl = curatorFramework.acl.forPath("/services")
    assertThat(Iterables.getOnlyElement(servicesAcl))
        .isEqualTo(ACL(SHARED_DIR_PERMS, ZooDefs.Ids.ANYONE_ID_UNSAFE))
  }
}