package misk.perf

import com.google.common.base.Ticker
import jakarta.inject.Qualifier
import misk.concurrent.Sleeper

/** Used to bind a [Sleeper] and [Ticker] that are suitable for usage by the [PauseDetector] */
@Qualifier
@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD, AnnotationTarget.TYPE)
annotation class ForPauseDetector
