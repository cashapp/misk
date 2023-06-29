package misk.cloud.gcp.testing

import com.google.api.client.http.HttpTransport

@Deprecated("Replace the dependency on misk-gcp-testing with testFixtures(misk-gcp)")
class FakeHttpRouter(val router: (FakeHttpRequest) -> FakeHttpResponse) : HttpTransport() {
  companion object {
    fun respondWithJson(item: Any) = FakeHttpResponse()
      .setStatusCode(200)
      .setJsonContent(item)

    fun respondWithError(statusCode: Int) = FakeHttpResponse().setStatusCode(statusCode)

    fun respondWithText(text: String) = respondWithText(200, text)

    fun respondWithText(statusCode: Int, text: String) =
      FakeHttpResponse().setStatusCode(statusCode).setContent(text)
  }

  override fun buildRequest(method: String, url: String) = FakeHttpRequest(method, url, router)
}
