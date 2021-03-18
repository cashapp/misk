package misk.web.exceptions

import misk.exceptions.ActionException
import misk.exceptions.StatusCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.event.Level

internal class ActionExceptionMapperTest {

  @Test
  fun clientExceptionLoggingLevel() {
    val config = ActionExceptionLogLevelConfig(client_error_level = Level.INFO)
    val mapper = ActionExceptionMapper(config)
    assertThat(mapper.loggingLevel(ActionException(StatusCode.NOT_FOUND)))
      .isEqualTo(Level.INFO)
  }

  @Test
  fun serverExceptionLoggingLevel() {
    val config = ActionExceptionLogLevelConfig(server_error_level = Level.INFO)
    val mapper = ActionExceptionMapper(config)
    assertThat(mapper.loggingLevel(ActionException(StatusCode.SERVICE_UNAVAILABLE)))
      .isEqualTo(Level.INFO)
  }
}
