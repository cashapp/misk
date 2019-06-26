package misk.resources

import com.google.inject.util.Modules
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = false)
class FakeFileLoaderTest {

  @MiskTestModule
  private val module = Modules.combine(TestingResourceLoaderModule(), FakeFilesModule())

  @Inject private lateinit var loader: ResourceLoader

  @Test fun loadFileFromMemory() {
    assertThat(loader.utf8("filesystem:/some/test/file")).contains("test data!")
  }

  @Test fun fileDoesNotExist() {
    assertThat(loader.exists("filesystem:/does/not/exist"))
        .isFalse()
    assertThat(loader.utf8("filesystem:/does/not/exist")).isNull()
  }

  private class FakeFilesModule : KAbstractModule() {
    override fun configure() {
      newMapBinder<String, String>(ForFakeFiles::class)
          .addBinding("/some/test/file")
          .toInstance("test data!")
    }
  }
}
