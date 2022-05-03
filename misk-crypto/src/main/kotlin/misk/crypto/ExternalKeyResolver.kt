package misk.crypto

import com.google.inject.Inject
import wisp.logging.getLogger

/**
 * [ExternalKeyResolver] implements an [KeyResolver] that fetches Tink keysets from an external
 * source, such as an S3 bucket. If multiple sources are registered (by binding implementations
 * of [KeyResolver]), the first one to contain the key (via [ExternalKeySource.keyExists]) is
 * the key that is used.
 *
 * If a key is not found, an [ExternalKeyManagerException] exception is raised.
 */
class ExternalKeyResolver @Inject constructor(
  @ExternalDataKeys override val allKeyAliases: Map<KeyAlias, KeyType>,

  private val externalKeySources: Set<ExternalKeySource>,
) : KeyResolver {

  private fun getRemoteKey(alias: KeyAlias): Key {
    for (keySource in externalKeySources) {
      logger.info("getRemoteKey: checking ${keySource::class.qualifiedName} for $alias")
      if (keySource.keyExists(alias)) {
        keySource.getKey(alias)?.let { return it }
        logger.warn("$alias is reported to exist, but not able to retrieve; continuing")
      }
    }

    throw ExternalKeyManagerException("$alias not accessible (checked ${externalKeySources.size} registered sources)")
  }

  // Injector tests initialize key managers in non-native environments, so we delegate creation
  // until needed.
  private val keys: Map<KeyAlias, Key> by lazy {
    allKeyAliases.mapValuesTo(linkedMapOf()) { (alias, _) ->
      getRemoteKey(alias).also { logger.info("loaded ${it.key_name}") }
    }
  }

  override fun getKeyByAlias(alias: KeyAlias) = keys[alias]

  companion object {
    private val logger = getLogger<ExternalKeyResolver>()
  }
}
