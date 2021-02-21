package misk.clustering.fake

import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.clustering.Cluster
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class FakeClusterTest {
  @MiskTestModule val module = Modules.combine(
    MiskTestingServiceModule(),
    FakeClusterModule()
  )

  @Inject lateinit var cluster: FakeCluster

  @Test fun clusterRespondsToChanges() {
    cluster.clusterChanged(membersBecomingReady = setOf(Cluster.Member("blerp", "192.168.12.3")))
    assertThat(cluster.snapshot.readyMembers)
      .containsExactlyInAnyOrder(Cluster.Member("blerp", "192.168.12.3"))
    cluster.clusterChanged(membersBecomingNotReady = setOf(Cluster.Member("blerp", "192.168.12.3")))
    assertThat(cluster.snapshot.readyMembers).isEmpty()
  }

  @Test fun clusterUsesExplicitResourceMapping() {
    // By default all resources should be owned by us
    assertThat(cluster.resourceMapper["my-object"]).isEqualTo(
      FakeCluster.self
    )

    cluster.resourceMapper.setDefaultMapping(Cluster.Member("zork", "192.168.12.0"))
    cluster.resourceMapper.addMapping("my-object", Cluster.Member("bork", "192.168.12.1"))

    assertThat(cluster.snapshot.resourceMapper["my-object"].name).isEqualTo("bork")
    assertThat(cluster.snapshot.resourceMapper["other-object"].name).isEqualTo("zork")

    // Ensure resource mapper remains the same even through cluster changes
    cluster.clusterChanged(membersBecomingReady = setOf(Cluster.Member("blerp", "192.168.12.3")))
    assertThat(cluster.snapshot.resourceMapper["my-object"].name).isEqualTo("bork")
    assertThat(cluster.snapshot.resourceMapper["other-object"].name).isEqualTo("zork")

    cluster.resourceMapper.removeMapping("my-object")
    assertThat(cluster.snapshot.resourceMapper["my-object"].name).isEqualTo("zork")
  }
}
