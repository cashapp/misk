package misk.resources

import java.io.IOException
import okio.Buffer
import okio.BufferedSource
import okio.source

/**
 * Read-only resources that are fetched from the 1password-cli (`op`) tool.
 *
 * To use, please:
 * 1. ensure the 1password-cli is installed with `hermit install op` or `brew install 1password-cli`.
 * 2. make sure to enable the cli integration in the 1password app:
 *    https://developer.1password.com/docs/cli/app-integration/#step-1-turn-on-the-app-integration.
 * 3. check that the secret being accessed is available. You can get the secret-reference by following:
 *    https://developer.1password.com/docs/cli/secret-references/#step-1-get-secret-references
 *
 * This uses the scheme `1password:`. Secret-references from 1password can therefore be used after copy-pasting and
 * replacing "op" like so: "1password://secretRef/goes/here". To use a specific account, add the accountId like so
 * "1password:accountId@//secretRef/goes/here".
 */
object OnePasswordResourceLoaderBackend : ResourceLoader.Backend() {
  const val SCHEME = "1password:"

  override fun checkPath(path: String) {
    OnePasswordResourcePath.fromPath(path)
  }

  override fun list(path: String): List<String> {
    require(path.isNotEmpty())

    return listOf(OnePasswordResourcePath.fromPath(path).toString())
  }

  override fun exists(path: String): Boolean {
    return try {
      fetch(path, "type").size > 0
    } catch (e: Exception) {
      false
    }
  }

  override fun open(path: String): BufferedSource? {
    return fetch(path)
  }

  private fun fetch(path: String, attribute: String? = null): Buffer {
    val resource = OnePasswordResourcePath.fromPath(path)
    val command = listOf("op", "read") + resource.asCliArgs(attribute)
    try {
      val process = ProcessBuilder().command(command).start()

      val exitCode = process.waitFor()
      if (exitCode == 0) {
        return Buffer().apply { writeAll(process.inputStream.source()) }
      }

      throw NoSuchElementException(
        "1Password secret $resource could not be found! Please make sure it is available via: `${command.joinToString(" ")}`"
      )
    } catch (e: IOException) {
      throw UnsupportedOperationException(
        "Error calling the 1password-cli. Please ensure it is installed with `hermit install op` or `brew install 1password-cli`",
        e,
      )
    }
  }
}

/**
 * Represents a 1password secret-reference, with an optional extra account identifier to differentiate if there are
 * multiple 1password accounts. The secret-reference schema is documented at
 * https://developer.1password.com/docs/cli/secret-reference-syntax/. The only change for this use case is the "op:"
 * prefix is not present as the ResourceLoader implementation strips the schema.
 */
class OnePasswordResourcePath private constructor(val account: String?, val secretReference: String) {
  override fun toString(): String {
    return account?.let { "$it@$secretReference" } ?: secretReference
  }

  @JvmOverloads
  fun asCliArgs(attribute: String? = null): List<String> {
    // For checking a specific attribute of the secret rather than the value (default)
    val attributeField = attribute?.let { "?attribute=$it" } ?: ""
    // Add `op:` prefix for the 1password-cli
    val secretRef = "op:$secretReference$attributeField"
    // Include account args, if specified
    if (account != null) {
      return listOf("--no-newline", "--account", account, secretRef)
    }
    return listOf("--no-newline", secretRef)
  }

  companion object {
    /** Expects a path in the form "accountId@//secretRef/goes/here" or "//secretRef/goes/here" */
    fun fromPath(path: String): OnePasswordResourcePath {
      if (path.contains("@")) {
        val (account, secretReference) = path.split("@", limit = 2)
        require(secretReference.startsWith("//")) { "1Password secret reference must start with //" }
        return OnePasswordResourcePath(account, secretReference)
      }

      require(path.startsWith("//")) { "1Password secret reference must start with //" }
      return OnePasswordResourcePath(null, path)
    }
  }
}
