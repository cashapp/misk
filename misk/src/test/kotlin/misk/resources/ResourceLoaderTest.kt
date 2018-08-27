package misk.resources

import com.google.inject.util.Modules
import misk.MiskServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TemporaryFolder
import misk.testing.TemporaryFolderModule
import okio.buffer
import okio.sink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
class ResourceLoaderTest {
  @MiskTestModule
  val module = Modules.combine(MiskServiceModule(), TemporaryFolderModule())

  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject lateinit var tempFolder: TemporaryFolder

  @Test
  fun loadResource() {
    val resource = resourceLoader.utf8("classpath:/misk/resources/ResourceLoaderTest.txt")!!
    assertThat(resource).isEqualTo("69e0753934d2838d1953602ca7722444\n")
  }

  @Test
  fun absentResource() {
    assertThat(resourceLoader.utf8("classpath:/misk/resources/NoSuchResource.txt")).isNull()
  }

  @Test
  fun listContainsImmediateChild() {
    assertThat(resourceLoader.list("classpath:/misk/resources/"))
        .contains("classpath:/misk/resources/ResourceLoaderTest.txt")
  }

  @Test
  fun listDoesNotContainChildOfChild() {
    assertThat(resourceLoader.list("classpath:/misk/"))
        .doesNotContain("classpath:/misk/resources/ResourceLoaderTest.txt")
  }

  @Test
  fun memoryResources() {
    val data1 = "memory:/misk/resources/data1.txt"
    val data2 = "memory:/misk/resources/data2.txt"
    resourceLoader.put(data1, "foo")
    resourceLoader.put(data2, "bar")

    assertThat(resourceLoader.exists(data1)).isTrue()
    assertThat(resourceLoader.exists(data2)).isTrue()
    assertThat(resourceLoader.exists("memory:/misk/resources/data3.txt")).isFalse()

    assertThat(resourceLoader.open(data1)!!.readUtf8()).isEqualTo("foo")
    assertThat(resourceLoader.utf8(data1)).isEqualTo("foo")

    assertThat(resourceLoader.list(data1)).isEmpty()
    assertThat(resourceLoader.list("memory:/misk/resources/data1.txt/")).isEmpty()
    assertThat(resourceLoader.list("memory:/misk/res")).isEmpty()

    assertThat(resourceLoader.list("memory:/misk/resources")).containsExactly(data1, data2)
    assertThat(resourceLoader.list("memory:/misk/resources/")).containsExactly(data1, data2)
    assertThat(resourceLoader.list("memory:/misk")).containsExactly("memory:/misk/resources")
    assertThat(resourceLoader.list("memory:/misk/")).containsExactly("memory:/misk/resources")
  }

  @Test
  fun filesystemResources() {
    val tempRoot = tempFolder.root.toAbsolutePath().toFile()
    assertThat(tempRoot.toString()).startsWith("/") // Tests below require this.

    val resource1 = "filesystem:$tempRoot/data1.txt"
    File(tempRoot, "data1.txt").sink().buffer().use {
      it.writeUtf8("foo")
    }

    val resource2 = "filesystem:$tempRoot/data2.txt"
    File(tempRoot, "data2.txt").sink().buffer().use {
      it.writeUtf8("bar")
    }

    val resource3 = "filesystem:$tempRoot/data3.txt"

    assertThat(resourceLoader.exists(resource1)).isTrue()
    assertThat(resourceLoader.exists(resource2)).isTrue()
    assertThat(resourceLoader.exists(resource3)).isFalse()

    resourceLoader.open(resource1)!!.use {
      assertThat(it.readUtf8()).isEqualTo("foo")
    }
    assertThat(resourceLoader.utf8(resource2)).isEqualTo("bar")

    assertFailsWith<UnsupportedOperationException> {
      resourceLoader.list("filesystem:$tempRoot")
    }
  }

  @Test
  fun addressValidation() {
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open(":")
    }
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open("")
    }
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open(":/")
    }
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open("a:/")
    }
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open("a://")
    }
  }

  @Test
  fun unknownBackend() {
    assertThat(resourceLoader.utf8("unknown:/misk/resources/ResourceLoaderTest.txt")).isNull()
    assertThat(resourceLoader.list("unknown:/misk/resources/")).isEmpty()
  }
}
