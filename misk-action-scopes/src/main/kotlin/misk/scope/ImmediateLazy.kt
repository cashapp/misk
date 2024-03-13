package misk.scope

internal class ImmediateLazy(override val value: Any?) : Lazy<Any?> {
  override fun isInitialized(): Boolean = true
}
