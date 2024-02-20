package misk.web.jetty

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JettyServiceTest {

  @Test
  fun isJEP380Supported() {
    assertThat(isJEP380Supported("", 16)).isFalse()
    assertThat(isJEP380Supported("@socket.sock", 16)).isFalse()
    assertThat(isJEP380Supported("\u0000socket.sock", 16)).isFalse()
    assertThat(isJEP380Supported("socket.sock", 15)).isFalse()
    assertThat(isJEP380Supported("socket.sock", 16)).isTrue()
    assertThat(isJEP380Supported("/socket.sock", 16)).isTrue()
  }
}
