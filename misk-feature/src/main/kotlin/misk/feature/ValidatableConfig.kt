package misk.feature

import misk.config.Config

/**
 * Interface used by [ConsumerConfigResolver] to support validation of retrieved configs.
 */
interface ValidatableConfig<T> : Config {
  fun validate()
}