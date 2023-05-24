package misk.cloud.gcp.testing

import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.testing.http.MockLowLevelHttpResponse

typealias FakeHttpResponse = MockLowLevelHttpResponse

fun FakeHttpResponse.setJsonContent(item: Any): FakeHttpResponse =
  setContent(JacksonFactory.getDefaultInstance().toByteArray(item))
