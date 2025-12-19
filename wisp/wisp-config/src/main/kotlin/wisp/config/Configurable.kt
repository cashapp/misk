package wisp.config

import kotlin.reflect.KClass

interface Configurable<T : Config> {
  fun configure(config: T)

  fun getConfigClass(): KClass<T>
}
