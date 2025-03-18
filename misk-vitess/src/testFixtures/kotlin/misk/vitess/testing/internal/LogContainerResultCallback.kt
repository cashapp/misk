package misk.vitess.testing.internal

import com.github.dockerjava.api.async.ResultCallbackTemplate
import com.github.dockerjava.api.model.Frame

class LogContainerResultCallback : ResultCallbackTemplate<LogContainerResultCallback, Frame>() {
  private val logBuilder = StringBuilder()

  override fun onNext(item: Frame) {
    val logMessage = String(item.payload).trim()
    logBuilder.append(logMessage).append("\n")
  }

  fun getLogs(): String = "$logBuilder"
}
