package misk.logging

import misk.config.Config

data class DynamicLoggingConfig
@JvmOverloads
constructor(val enabled: Boolean = false, val feature_flag_name: String = "") : Config
