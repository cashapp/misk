package misk.fastpath

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerFastpath @Inject constructor() {
  var collecting = false
  val events = mutableListOf<String>()
}
