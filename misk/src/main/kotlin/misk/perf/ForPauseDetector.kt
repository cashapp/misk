package misk.perf

import com.google.common.base.Ticker
import misk.concurrent.Sleeper
import com.google.inject.BindingAnnotation
import jakarta.inject.Qualifier

/**
 * Used to bind a [Sleeper] and [Ticker] that are suitable for usage by the [PauseDetector]
 */
@Qualifier
@BindingAnnotation
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.VALUE_PARAMETER,
  AnnotationTarget.FIELD,
  AnnotationTarget.TYPE
)
annotation class ForPauseDetector
