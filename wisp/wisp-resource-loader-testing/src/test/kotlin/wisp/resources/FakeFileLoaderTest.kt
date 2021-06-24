package wisp.resources

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FakeFileLoaderTest {
  private val loader = ResourceLoader(
    mapOf(
      "filesystem:" to FakeFilesystemLoaderBackend(
        mapOf(
          "/some/test/file" to "test data!"
        )
      )
    )
  )

  @Test fun loadFileFromMemory() {
    assertThat(loader.utf8("filesystem:/some/test/file")).contains("test data!")
  }

  @Test fun fileDoesNotExist() {
    assertThat(loader.exists("filesystem:/does/not/exist")).isFalse()
    assertThat(loader.utf8("filesystem:/does/not/exist")).isNull()
  }
}
