package misk.resources

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
class ResourceLoaderTest {
  @MiskTestModule
  val module = ResourceLoaderModule()

  @Inject lateinit var resourceLoader: ResourceLoader

  @Test
  fun loadResource() {
    val resource = resourceLoader.utf8("/resources/misk/resources/ResourceLoaderTest.txt")!!
    assertThat(resource).isEqualTo("69e0753934d2838d1953602ca7722444\n")
  }

  @Test
  fun absentResource() {
    assertThat(resourceLoader.utf8("/resources/misk/resources/NoSuchResource.txt")).isNull()
  }

  @Test
  fun listContainsImmediateChild() {
    assertThat(resourceLoader.list("/resources/misk/resources/"))
        .contains("/resources/misk/resources/ResourceLoaderTest.txt")
  }

  @Test
  fun listDoesNotContainChildOfChild() {
    assertThat(resourceLoader.list("/resources/misk/"))
        .doesNotContain("/resources/misk/resources/ResourceLoaderTest.txt")
  }

  @Test
  fun memoryResources() {
    val data1 = "/memory/misk/resources/data1.txt"
    val data2 = "/memory/misk/resources/data2.txt"
    resourceLoader.put(data1, "foo")
    resourceLoader.put(data2, "bar")

    assertThat(resourceLoader.exists(data1)).isTrue()
    assertThat(resourceLoader.exists(data2)).isTrue()
    assertThat(resourceLoader.exists("/memory/misk/resources/data3.txt")).isFalse()

    assertThat(resourceLoader.open(data1)!!.readUtf8()).isEqualTo("foo")
    assertThat(resourceLoader.utf8(data1)).isEqualTo("foo")

    assertThat(resourceLoader.list(data1)).isEmpty()
    assertThat(resourceLoader.list("/memory/misk/resources/data1.txt/")).isEmpty()
    assertThat(resourceLoader.list("/memory/misk/res")).isEmpty()

    assertThat(resourceLoader.list("/memory/misk/resources")).containsExactly(data1, data2)
    assertThat(resourceLoader.list("/memory/misk/resources/")).containsExactly(data1, data2)
    assertThat(resourceLoader.list("/memory/misk")).containsExactly("/memory/misk/resources")
    assertThat(resourceLoader.list("/memory/misk/")).containsExactly("/memory/misk/resources")
  }

  @Test
  fun pathValidation() {
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open("")
    }
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open("//")
    }
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open("/a//")
    }
  }

  @Test
  fun unknownBackend() {
    assertThat(resourceLoader.utf8("/unknown/misk/resources/ResourceLoaderTest.txt")).isNull()
    assertThat(resourceLoader.list("/unknown/misk/resources/")).isEmpty()
  }
}
