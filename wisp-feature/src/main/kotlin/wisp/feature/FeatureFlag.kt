package wisp.feature

import kotlin.reflect.KClass

sealed interface FeatureFlag<T : Any> {
  /**
   * Feature name of the feature flag
   */
  val feature: Feature

  /**
   * Unique primary key for the entity the flag should be evaluated against.
   */
  val key: String

  /**
   * The attributes of this feature flag, provided during flag evaluation
   */
  val attributes: Attributes
}

interface StringFeatureFlag : FeatureFlag<String>
interface BooleanFeatureFlag : FeatureFlag<Boolean>
interface IntFeatureFlag : FeatureFlag<Int>
interface DoubleFeatureFlag : FeatureFlag<Double>

/**
 * A Enumeration feature flag, when evaluated returns [T]
 */
interface EnumFeatureFlag<T : Enum<T>> : FeatureFlag<T> {
  val returnType: Class<out T>
}

/**
 * A JSON feature flag, when evaluated returns [T].
 *
 * It is expected that a Moshi type adapter is registered for [T].
 *
 * Example definition:
 *
 * ```
 * // Step 1: Define the object we expect to get from the JSON flag
 * data class PaymentConfiguration(
 *   val fraudulent: Boolean,
 *   val vipTreatment: Boolean,
 *   val specialDescription: String
 * )
 *
 * // Step 2: Define the feature flag
 * data class PaymentConfigurationFeature(
 *   // Put the `key` and `attributes` here
 *   val customerId: String,
 *   val extraAttribute: String
 * ) : JsonFeatureFlag<PaymentConfiguration> {
 *   override val feature = Feature("payment-configuration-feature")
 *   override val key = customerId
 *   override val attributes = Attributes()
 *     .with("extraAttribute", extraAttribute)
 *   override val returnType = PaymentConfiguration::class
 * }
 * ```
 */
interface JsonFeatureFlag<T : Any> : FeatureFlag<T> {
  val returnType: Class<out T>
}
