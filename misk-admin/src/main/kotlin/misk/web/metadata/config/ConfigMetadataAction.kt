package misk.web.metadata.config

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.actions.WebAction

@Singleton
class ConfigMetadataAction @Inject constructor() : WebAction {
  enum class ConfigTabMode {
    /** Only show safe content which will not leak Misk secrets */
    SAFE,
    /**
     * Show redacted effective config loaded into application, risk of leak if sensitive non-Secret fields don't
     * have @misk.config.Redact annotation manually added.
     */
    SHOW_REDACTED_EFFECTIVE_CONFIG,
    /** Shows all possible resources, YAML files are not redacted */
    UNSAFE_LEAK_MISK_SECRETS,
  }
}
