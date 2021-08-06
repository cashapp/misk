package wisp.config

interface Configurable<T : Config> {
  fun configure(config: T)

  fun getConfigClass(): Class<T>
}
