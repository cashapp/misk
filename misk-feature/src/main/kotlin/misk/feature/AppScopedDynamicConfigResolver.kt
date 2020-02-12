package misk.feature

/**
 * Abstraction for dynamically retrieving dynamic config overrides, scoped to the calling app.
 */
interface AppScopedDynamicConfigResolver<T : ValidatableConfig<T>> {
  /**
   * Resolves the named configuration scoped to the calling application.
   * If the underlying feature is not found, or its JSON representation cannot be interpreted,
   * returns the configured failure mode default and warns.
   */
  fun resolveConfig(): T
}
