package com.squareup.exemplar

import com.google.common.truth.Truth.assertThat
import misk.testing.ActionTest
import okhttp3.Headers
import org.junit.jupiter.api.Test
import javax.inject.Inject

@ActionTest
class HelloWebActionTest {
  @Inject lateinit var helloWebAction: HelloWebAction

  @Test
  fun test() {
    assertThat(helloWebAction.hello("sandy", Headers.of())).isEqualTo(HelloResponse("YO", "SANDY"))
  }
}
