package misk.fastpath

import javax.inject.Inject
import javax.inject.Singleton

// TODO: single fastpath interface (merge with ServerFastpath)
@Singleton
class ClientFastpath @Inject constructor() {
  var collecting = false
  val events = mutableListOf<String>()
}
