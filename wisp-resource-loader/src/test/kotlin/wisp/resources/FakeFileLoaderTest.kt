package wisp.resources

import com.google.common.collect.ImmutableMap
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FakeFileLoaderTest {
  private val loader = ResourceLoader(
    ImmutableMap.of(
      "filesystem:",
      FakeFilesystemLoaderBackend(
        mapOf(
          "/some/test/file" to "test data!"
        )
      )
    ) as java.util.Map<String, ResourceLoader.Backend>
  )

  @Test fun loadFileFromMemory() {
    assertThat(loader.utf8("filesystem:/some/test/file")).contains("test data!")
  }

  @Test fun fileDoesNotExist() {
    assertThat(loader.exists("filesystem:/does/not/exist")).isFalse()
    assertThat(loader.utf8("filesystem:/does/not/exist")).isNull()
  }
}
