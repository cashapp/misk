package misk.feature

/**
 * Interface for evaluating dynamic flags. Dynamic flags are similar to feature flags, but they
 * don't support different variations for different keys.
 */
// Soft deprecation
interface DynamicConfig : wisp.feature.DynamicConfig

// Soft deprecation
interface TrackerReference : wisp.feature.TrackerReference

