package misk.resources

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class FakeResourceLoaderTest {
  @MiskTestModule
  val module = FakeResourceLoaderModule()

  @Inject lateinit var resourceLoader: FakeResourceLoader

  @Test
  fun happyPath() {
    val data1 = "misk/resources/data1.txt"
    val data2 = "misk/resources/data2.txt"
    resourceLoader.put(data1, "foo")
    resourceLoader.put(data2, "bar")

    assertThat(resourceLoader.exists(data1)).isTrue()
    assertThat(resourceLoader.exists(data2)).isTrue()
    assertThat(resourceLoader.exists("misk/resources/data3.txt")).isFalse()

    assertThat(resourceLoader.open(data1)!!.readUtf8()).isEqualTo("foo")
    assertThat(resourceLoader.utf8(data1)).isEqualTo("foo")

    assertThat(resourceLoader.list(data1)).isEmpty()
    assertThat(resourceLoader.list("misk/resources/data1.txt/")).isEmpty()
    assertThat(resourceLoader.list("misk/res")).isEmpty()

    assertThat(resourceLoader.list("misk/resources")).containsExactly(data1, data2)
    assertThat(resourceLoader.list("misk/resources/")).containsExactly(data1, data2)
    assertThat(resourceLoader.list("misk")).containsExactly("misk/resources")
    assertThat(resourceLoader.list("misk/")).containsExactly("misk/resources")
  }
}