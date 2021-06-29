package misk.feature

interface TrackerReference : wisp.feature.TrackerReference

fun wisp.feature.TrackerReference.toMisk(): TrackerReference {
  val delegate = this
  return object : TrackerReference {
    override fun unregister() {
      delegate.unregister()
    }
  }
}
