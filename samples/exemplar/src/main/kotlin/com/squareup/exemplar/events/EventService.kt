package com.squareup.exemplar.events

import com.google.common.util.concurrent.AbstractIdleService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import misk.metrics.v2.Metrics
import wisp.logging.getLogger
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

@Singleton
class EventService @Inject constructor (
  private val metrics : Metrics
) : AbstractIdleService() {

  private val run = AtomicBoolean(true)

  override fun startUp() {
    val runGuage = metrics.gauge(
      "event_service_running_events",
      "Counts events being processed"
    )
    val file = File("events.txt") // file has 100000 lines
    if (!file.exists()) {
      file.createNewFile()
    }
    val fileStream = FileInputStream(file)
    val eventReader = BufferedReader(InputStreamReader(fileStream))
    thread {
      runBlocking() {
        while (run.get()) {
          val event = eventReader.readLine()
          if (event == null) {
            delay(100)
          } else {
            launch {
              runCatching {
                withContext(Dispatchers.IO) {
                  runGuage.inc()
                  log.info("Processing event: $event")
                  Thread.sleep(100)
                  runGuage.dec()
                }
              }
            }
          }
        }
      }
    }
  }

  override fun shutDown() {
    run.set(false)
  }

  private companion object {
    val log = getLogger<EventService>()
  }
}
