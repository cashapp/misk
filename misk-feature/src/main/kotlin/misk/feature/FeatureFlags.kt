package misk.feature

// soft deprecation for wisp.feature...

/**
 * Interface for evaluating feature flags.
 */
interface FeatureFlags : wisp.feature.FeatureFlags

/**
 * Typed feature string.
 */
class Feature(name: String) : wisp.feature.Feature(name)

/**
 * Extra attributes to be used for evaluating features.
 */
class Attributes @JvmOverloads constructor(
  text: Map<String, String> = mapOf(),
  number: Map<String, Number>? = null,
  anonymous: Boolean = false
) : wisp.feature.Attributes(text, number, anonymous)
