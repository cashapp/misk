package com.squareup.exemplar.actions

import javax.inject.Inject

interface EventSubscriberGrpc {
  fun handleEvent(event: String)
}

interface EventClient {
  fun handleEvent(event: String)
}


class ClientSyncEventHandler : EventSubscriberGrpc {
  override fun handleEvent(event: String) {
    TODO("Not yet implemented")
  }
}

// Exposed as a library
class ClientSyncEventClient : EventClient {
  override fun handleEvent(event: String) {
    TODO("Not yet implemented")
  }
}


class PlasmaEventHandler : EventSubscriberGrpc {
  override fun handleEvent(event: String) {
    TODO("Not yet implemented")
  }
}

class Evently @Inject constructor(
  eventHandler: Router<EventSubscriberGrpc>
) {




}

interface Router<T> {


  fun implementations(): Map<Endpoint, T>

}

data class Endpoint(name: String)
