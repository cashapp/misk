package misk.docker

import java.nio.file.Path
import misk.testing.MiskTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@MiskTest
class DockerCredentialsTest {
  @Test
  fun returnsNullWhenConfigDoesNotExist(@TempDir tempDir: Path) {
    val result = DockerCredentials.getDockerCredentials("https://index.docker.io/v1/", FakeFileSystem())
    assertThat(result).isNull()
  }

  @Test
  fun returnsNullWhenCredsStoreMissing(@TempDir tempDir: Path) {
    val fs = writeDockerConfig {
      """
      {
        "auths": {
          "https://index.docker.io/v1/": {}
        }
      }
      """
        .trimIndent()
    }

    val result = DockerCredentials.getDockerCredentials("https://index.docker.io/v1/", fs)
    assertThat(result).isNull()
  }

  @Test
  fun returnsCredentialsWhenCredsStoreExists(@TempDir tempDir: Path) {
    // Writes docker config with credsStore pointing to a fake that returns a username and secret
    val fs = writeDockerConfig {
      """
      {
        "auths": {
          "https://index.docker.io/v1/": {}
        },
        "credsStore": "fake"
      }
      """
        .trimIndent()
    }

    val result = DockerCredentials.getDockerCredentials("https://index.docker.io/v1/", fs)
    assertThat(result).isNotNull()
    assertThat(result!!.username).isEqualTo("testuser")
    assertThat(result.password).isEqualTo("testpassword")
  }

  @Test
  fun returnsNullWhenCredsStoreReturnsEmpty(@TempDir tempDir: Path) {
    // Writes docker config with credsStore pointing to a fake that returns {}
    val fs = writeDockerConfig {
      """
      {
        "auths": {
          "https://index.docker.io/v1/": {}
        },
        "credsStore": "fake-empty"
      }
      """
        .trimIndent()
    }

    val result = DockerCredentials.getDockerCredentials("https://index.docker.io/v1/", fs)
    assertThat(result).isNotNull()
    assertThat(result?.username).isNull()
    assertThat(result?.password).isNull()
  }

  private fun writeDockerConfig(dockerConfig: () -> String) =
    FakeFileSystem().apply {
      createDirectories("/fake/.docker/".toPath())
      write("/fake/.docker/config.json".toPath()) { writeUtf8(dockerConfig()) }
    }

  companion object {
    @JvmStatic
    @BeforeAll
    fun beforeAll(): Unit {
      System.setProperty("user.home", "/fake/")
    }
  }
}
