package misk.logging

import misk.config.Config

data class DynamicLoggingConfig(val enabled: Boolean = false, val feature_flag_name: String = "") :
  Config