package misk.resources

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import wisp.resources.OnePasswordResourceLoaderBackend
import wisp.resources.OnePasswordResourcePath
import wisp.resources.ResourceLoader
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnePasswordResourceLoaderBackendTest {

  private val resourceLoader: ResourceLoader = ResourceLoader(
      mapOf(
          OnePasswordResourceLoaderBackend.SCHEME to OnePasswordResourceLoaderBackend,
      )
  )

  private val nonExistentOnePasswordResource = "1password://Employee/Test login for unit tests/does-not-exist"
  private val existingOnePasswordResource = "1password://Employee/Test login for unit tests/password"
  private val existingOnePasswordResourceWithAccount = "1password:squareup.1password.com@//Employee/Test login for unit tests/password"


  @Test
  fun onePasswordResourcePath() {
    val resourcePath = OnePasswordResourcePath.Companion.fromPath("//My Vault Name/My Secret Name/field_name_here")
    assertThat(resourcePath.account).isNull()
    assertEquals(resourcePath.secretReference, "//My Vault Name/My Secret Name/field_name_here")
    assertEquals(resourcePath.asCliArgs(), listOf("--no-newline", "op://My Vault Name/My Secret Name/field_name_here"))
    assertEquals(resourcePath.asCliArgs(attribute = "type"), listOf("--no-newline", "op://My Vault Name/My Secret Name/field_name_here?attribute=type"))
  }

  @Test
  fun onePasswordResourcePathWithAccountId() {
    val resourcePath = OnePasswordResourcePath.Companion.fromPath("myAccount.1password.com@//My Vault Name/My Secret Name/field_name_here")
    assertEquals(resourcePath.account, "myAccount.1password.com")
    assertEquals(resourcePath.secretReference, "//My Vault Name/My Secret Name/field_name_here")
    assertEquals(resourcePath.asCliArgs(), listOf("--no-newline", "--account", "myAccount.1password.com", "op://My Vault Name/My Secret Name/field_name_here"))
    assertEquals(resourcePath.asCliArgs(attribute = "type"), listOf("--no-newline", "--account", "myAccount.1password.com", "op://My Vault Name/My Secret Name/field_name_here?attribute=type"))
  }

  @Test
  fun onePasswordResourcePathInvalidFormat() {
    assertFailsWith<IllegalArgumentException> {
      OnePasswordResourcePath.Companion.fromPath("Malformed Name/field_name_here")
    }

    assertFailsWith<IllegalArgumentException> {
      OnePasswordResourcePath.Companion.fromPath("myAccount.1password.com@Malformed Name/field_name_here")
    }
  }

  @Disabled("Requires no `op` binary on path")
  @Test
  fun onePasswordMissingBinary() {
    assertFailsWith<UnsupportedOperationException> {
      resourceLoader.utf8(existingOnePasswordResource)
    }
  }

  @Disabled("Requires `op` binary on path")
  @Test
  fun onePasswordMissingSecret() {
    assertThat(resourceLoader.exists(nonExistentOnePasswordResource)).isFalse()
    assertFailsWith<NoSuchElementException> {
      resourceLoader.utf8(nonExistentOnePasswordResource)
    }
  }

  @Disabled("Requires `op` binary on path and a test secret")
  @Test
  fun onePasswordReadsSecret() {
    assertThat(resourceLoader.exists(existingOnePasswordResource)).isTrue()
    assertThat(resourceLoader.exists(existingOnePasswordResourceWithAccount)).isTrue()

    assertEquals(resourceLoader.utf8(existingOnePasswordResource), "test-value-here")
    assertEquals(resourceLoader.utf8(existingOnePasswordResourceWithAccount), "test-value-here")
  }
}
