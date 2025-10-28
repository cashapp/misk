package misk.logging

import misk.config.Config

data class DynamicLoggingConfig(val enabled: Boolean = false, val featureFlagName: String = "") :
  Config