package com.squareup.exemplar

import com.google.inject.Module
import com.squareup.exemplar.actions.HelloResponse
import com.squareup.exemplar.actions.HelloWebAction
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.Headers.Companion.headersOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class HelloWebActionTest {
  @MiskTestModule val module: Module = ExemplarTestModule()

  @Inject private lateinit var helloWebAction: HelloWebAction

  @Test
  fun happyPath() {
    assertThat(helloWebAction.hello("sandy", headersOf(), null, null))
      .isEqualTo(HelloResponse("0000000000000000000000001", "SANDY"))
  }
}
