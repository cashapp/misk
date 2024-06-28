package misk.resources

import com.google.inject.util.Modules
import java.io.File
import java.net.URLClassLoader
import jakarta.inject.Inject
import kotlin.test.assertFailsWith
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TemporaryFolder
import misk.testing.TemporaryFolderModule
import okio.buffer
import okio.sink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import wisp.resources.ClasspathResourceLoaderBackend
import wisp.resources.FilesystemLoaderBackend
import wisp.resources.MemoryResourceLoaderBackend

@MiskTest
class ResourceLoaderTest {
  @MiskTestModule
  val module = Modules.combine(ResourceLoaderModule(), TemporaryFolderModule())

  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject lateinit var tempFolder: TemporaryFolder

  lateinit var tempRoot: File

  @BeforeEach
  internal fun setUp() {
    tempRoot = tempFolder.root.toAbsolutePath().toFile()
    assertThat(tempRoot.toString()).startsWith("/") // Tests below require this.
  }

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
  fun listContainsResourcesFromJar() {
    assertThat(resourceLoader.list("classpath:/META-INF/"))
      .contains("classpath:/META-INF/MANIFEST.MF")
  }

  @Test
  fun walk() {
    val resourcesBaseDir = "classpath:/misk/resources"
    assertThat(resourceLoader.walk("$resourcesBaseDir/nested/deeper")).isEqualTo(
      listOf("$resourcesBaseDir/nested/deeper/nested2.txt")
    )

    assertThat(resourceLoader.walk("$resourcesBaseDir/nested")).containsExactlyInAnyOrder(
      "$resourcesBaseDir/nested/nested.txt",
      "$resourcesBaseDir/nested/deeper/nested2.txt"
    )

    assertThat(resourceLoader.walk("$resourcesBaseDir/")).contains(
      "$resourcesBaseDir/nested/nested.txt",
      "$resourcesBaseDir/nested/deeper/nested2.txt"
    )
  }

  @Test
  fun memoryResources() {
    val data1 = "memory:/misk/resources/data1.txt"
    val data2 = "memory:/misk/resources/data2.txt"
    val data3 = "memory:/misk/data3.txt"
    val data4 = "memory:/misk/tmp/data4.txt"
    resourceLoader.put(data1, "foo")
    resourceLoader.put(data2, "bar")
    resourceLoader.put(data3, "baz")
    resourceLoader.put(data4, "qux")

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
    assertThat(resourceLoader.list("memory:/misk")).containsExactlyInAnyOrder(
      "memory:/misk/resources", "memory:/misk/tmp", data3
    )
    assertThat(resourceLoader.list("memory:/misk/")).containsExactlyInAnyOrder(
      "memory:/misk/resources", "memory:/misk/tmp", data3
    )

    assertThat(resourceLoader.walk("memory:/misk")).containsExactlyInAnyOrder(
      data1, data2, data3,
      data4
    )
  }

  @Test
  fun filesystemResources() {
    val resource1 = "filesystem:$tempRoot/data1.txt"
    File(tempRoot, "data1.txt").sink().buffer().use {
      it.writeUtf8("foo")
    }

    val resource2 = "filesystem:$tempRoot/data2.txt"
    File(tempRoot, "data2.txt").sink().buffer().use {
      it.writeUtf8("bar")
    }

    val resource3 = "filesystem:$tempRoot/data3.txt"

    val resource4 = "filesystem:$tempRoot/data4/data4.txt"
    File(tempRoot, "data4/").mkdirs()
    File(tempRoot, "data4/data4.txt").sink().buffer().use {
      it.writeUtf8("baz")
    }

    assertThat(resourceLoader.exists(resource1)).isTrue()
    assertThat(resourceLoader.exists(resource2)).isTrue()
    assertThat(resourceLoader.exists(resource3)).isFalse()
    assertThat(resourceLoader.exists(resource4)).isTrue()

    resourceLoader.open(resource1)!!.use {
      assertThat(it.readUtf8()).isEqualTo("foo")
    }
    assertThat(resourceLoader.utf8(resource2)).isEqualTo("bar")

    val topLevelResources = resourceLoader.list("filesystem:$tempRoot")
    assertThat(topLevelResources).containsExactlyInAnyOrder(resource1, resource2, "filesystem:$tempRoot/data4")

    val allResources = resourceLoader.walk("filesystem:$tempRoot")
    assertThat(allResources).containsExactlyInAnyOrder(resource1, resource2, resource4)
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
  }

  @ParameterizedTest
  @ValueSource(strings = [ClasspathResourceLoaderBackend.SCHEME, FilesystemLoaderBackend.SCHEME, MemoryResourceLoaderBackend.SCHEME])
  fun pathBasedAddressValidation(resource: String) {
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open("$resource:/")
    }
    assertFailsWith<IllegalArgumentException> {
      resourceLoader.open("$resource://")
    }
  }

  @Test
  fun unknownBackend() {
    assertThat(resourceLoader.utf8("unknown:/misk/resources/ResourceLoaderTest.txt")).isNull()
    assertThat(resourceLoader.list("unknown:/misk/resources/")).isEmpty()
  }

  @Test
  fun copyTo() {
    val tempRoot = tempFolder.root.toAbsolutePath()
    resourceLoader.copyTo("classpath:/misk/resources", tempRoot)
    val prefix = "filesystem:$tempRoot"
    assertThat(resourceLoader.utf8("$prefix/ResourceLoaderTest.txt")!!)
      .isEqualTo("69e0753934d2838d1953602ca7722444\n")
    assertThat(resourceLoader.utf8("$prefix/nested/nested.txt")!!)
      .isEqualTo("I am nested\n")
  }

  @Test
  fun classpathSchemeUsesContextClassLoader() {
    File(tempRoot, "context_class_loader_resource.txt").sink().buffer().use {
      it.writeUtf8("hello, context class loader")
    }
    val tempDirClassLoader = URLClassLoader(arrayOf(tempRoot.toURI().toURL()))

    // Confirm the resource isn't available in the app class loader.
    assertThat(resourceLoader.utf8("classpath:/context_class_loader_resource.txt"))
      .isNull()

    // But when the context class loader changes, the resource becomes visible.
    withContextClassLoader(tempDirClassLoader) {
      assertThat(resourceLoader.utf8("classpath:/context_class_loader_resource.txt"))
        .isEqualTo("hello, context class loader")
    }

    // The resource is not cached in the resource loader.
    assertThat(resourceLoader.utf8("classpath:/context_class_loader_resource.txt"))
      .isNull()
  }

  @Test
  fun contextClassLoaderForList() {
    val directory = File(tempRoot, "context_class_loader")
    directory.mkdirs()
    File(directory, "a.txt").sink().buffer().use {
      it.writeUtf8("A")
    }
    File(directory, "b.txt").sink().buffer().use {
      it.writeUtf8("B")
    }
    val tempDirClassLoader = URLClassLoader(arrayOf(tempRoot.toURI().toURL()))

    withContextClassLoader(tempDirClassLoader) {
      assertThat(resourceLoader.list("classpath:/context_class_loader")).contains(
        "classpath:/context_class_loader/a.txt",
        "classpath:/context_class_loader/b.txt"
      )
    }

    assertThat(resourceLoader.list("classpath:/context_class_loader")).isEmpty()
  }

  @Test
  fun contextClassLoaderForExists() {
    File(tempRoot, "context_class_loader_exists_resource.txt").sink().buffer().use {
      it.writeUtf8("hello, I exist")
    }
    val tempDirClassLoader = URLClassLoader(arrayOf(tempRoot.toURI().toURL()))

    withContextClassLoader(tempDirClassLoader) {
      assertThat(resourceLoader.exists("classpath:/context_class_loader_exists_resource.txt"))
        .isTrue()
    }

    assertThat(resourceLoader.exists("classpath:/context_class_loader_exists_resource.txt"))
      .isFalse()
  }

  private fun <T> withContextClassLoader(classLoader: ClassLoader, block: () -> T): T {
    val previousContextClassLoader = Thread.currentThread().contextClassLoader
    Thread.currentThread().contextClassLoader = classLoader
    try {
      return block()
    } finally {
      Thread.currentThread().contextClassLoader = previousContextClassLoader
    }
  }
}
