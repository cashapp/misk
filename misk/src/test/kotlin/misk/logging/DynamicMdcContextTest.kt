package misk.logging

import io.kotest.assertions.assertSoftly
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import wisp.logging.getLogger

class DynamicMdcContextTest {
  @BeforeEach
  fun setUp() {
    MDC.clear()
  }

  @Test
  fun `test consistently adding MDC elements `() {
    runBlocking(DynamicMdcContext()) {
      logger.info { "in runBlocking : ${MDC.getCopyOfContextMap()}" }
      MDC.put("level0", "value0").also {
        logger.info { "level0 added" }
      }
      launch {
        logger.info { "entering level1: ${MDC.getCopyOfContextMap()}" }
        MDC.put("level1", "value1").also {
          logger.info { "level1 added" }
        }
        launch {
          logger.info { "entering level2: ${MDC.getCopyOfContextMap()}" }
          MDC.put("level2", "value2").also {
            logger.info { "level2 added" }
          }
          assertSoftly {
            assertThat(MDC.get("level0")).isEqualTo("value0")
            assertThat(MDC.get("level1")).isEqualTo("value1")
            assertThat(MDC.get("level2")).isEqualTo("value2")
          }
        }
      }
    }
  }

  companion object {
    private val logger = getLogger<DynamicMdcContextTest>()
  }
}
