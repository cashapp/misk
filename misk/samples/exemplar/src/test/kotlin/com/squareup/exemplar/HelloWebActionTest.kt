package com.squareup.exemplar

import com.squareup.exemplar.actions.HelloResponse
import com.squareup.exemplar.actions.HelloWebAction
import misk.testing.MiskTest
import okhttp3.Headers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class HelloWebActionTest {
  @Inject lateinit var helloWebAction: HelloWebAction

  @Test
  fun happyPath() {
    assertThat(helloWebAction.hello("sandy", Headers.of(), null, null))
        .isEqualTo(HelloResponse("YO", "SANDY"))
  }
}
