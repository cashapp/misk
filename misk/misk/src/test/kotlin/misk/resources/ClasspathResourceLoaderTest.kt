package misk.resources

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class ClasspathResourceLoaderTest {
  @MiskTestModule
  val module = ResourceLoaderModule()

  @Inject lateinit var resourceLoader: ResourceLoader

  @Test
  fun loadResource() {
    val resource = resourceLoader.utf8("misk/resources/ClasspathResourceLoaderTest.txt")!!
    assertThat(resource).isEqualTo("69e0753934d2838d1953602ca7722444\n")
  }

  @Test
  fun absentResource() {
    assertThat(resourceLoader.utf8("misk/resources/NoSuchResource.txt")).isNull()
  }

  @Test
  fun listContainsImmediateChild() {
    assertThat(resourceLoader.list("misk/resources"))
        .contains("misk/resources/ClasspathResourceLoaderTest.txt")
  }

  @Test
  fun listDoesNotContainChildOfChild() {
    assertThat(resourceLoader.list("misk"))
        .doesNotContain("misk/resources/ClasspathResourceLoaderTest.txt")
  }
}
