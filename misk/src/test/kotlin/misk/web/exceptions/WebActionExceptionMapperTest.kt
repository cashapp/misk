package misk.web.exceptions

import misk.exceptions.NotFoundException
import misk.exceptions.ResourceUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.event.Level

internal class WebActionExceptionMapperTest {

  @Test
  fun clientExceptionLoggingLevel() {
    val config = ActionExceptionLogLevelConfig(client_error_level = Level.INFO)
    val mapper = WebActionExceptionMapper(config)
    assertThat(mapper.loggingLevel(NotFoundException()))
      .isEqualTo(Level.INFO)
  }

  @Test
  fun serverExceptionLoggingLevel() {
    val config = ActionExceptionLogLevelConfig(server_error_level = Level.INFO)
    val mapper = WebActionExceptionMapper(config)
    assertThat(mapper.loggingLevel(ResourceUnavailableException()))
      .isEqualTo(Level.INFO)
  }
}
