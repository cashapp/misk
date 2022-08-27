package com.squareup.exemplar

import com.squareup.library.actions.HelloResponse
import com.squareup.library.actions.HelloWebAction
import misk.testing.MiskTest
import okhttp3.Headers.Companion.headersOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class HelloWebActionTest {
  @Test
  fun happyPath() {
    assertThat(HelloWebAction().hello("sandy", headersOf(), null, null))
        .isEqualTo(HelloResponse("YO", "SANDY"))
  }
}
