package wisp.feature

object FeatureFlagValidation {

    /**
     * Validates the feature flags's hashing "key". Most implementation technically support arbitrary
     * strings, but we still prefer to restrict valid input to prevent accidentally passing
     * in the wrong value or potentially sensitive information.
     */
    fun checkValidKey(feature: Feature, key: String) {
        require(key.isNotEmpty()) { "Key to flag $feature must not be empty" }
    }
}
