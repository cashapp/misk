package misk.testing.okhttp

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

class RoutingDispatcher(val routes: (RecordedRequest) -> MockResponse) : Dispatcher() {
  override fun dispatch(request: RecordedRequest): MockResponse = routes.invoke(request)
}
