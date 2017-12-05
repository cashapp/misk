package com.squareup.exemplar

import com.google.common.truth.Truth.assertThat
import misk.testing.MiskTestRule
import okhttp3.Headers
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

class HelloWebActionTest {
  @Rule
  @JvmField
  val miskTestRule = MiskTestRule()

  @Inject lateinit var helloWebAction: HelloWebAction

  @Test
  fun test() {
    assertThat(helloWebAction.hello("sandy", Headers.of())).isEqualTo(HelloResponse("YO", "SANDY"))
  }
}
