/*
 * Copyright 2014 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copied and modified from gRPC-Java:
 * https://github.com/grpc/grpc-java/blob/83538cdae3142be122496fbaa80440f39d715a47/core/src/main/java/io/grpc/internal/GrpcUtil.java#L651
 * Original file licensed under Apache 2.0.
 *
 */

/**
 * Marshals a nanosecond timeout value to/from a string format used in gRPC headers.
 *
 * The string consists of up to 8 ASCII decimal digits followed by a unit:
 * - `n` = nanoseconds
 * - `u` = microseconds
 * - `m` = milliseconds
 * - `S` = seconds
 * - `M` = minutes
 * - `H` = hours
 *
 * The format is greedy with respect to precision — e.g., 2 seconds is represented as `2000000u`.
 *
 * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md#requests"> gRPC HTTP/2 request header
 *   format </a>
 */
package misk.grpc

import java.util.concurrent.TimeUnit

object GrpcTimeoutMarshaller {
  const val TIMEOUT_KEY = "grpc-timeout"

  fun toAsciiString(timeoutNanos: Long): String {
    val cutoff = 100_000_000L
    val unit = TimeUnit.NANOSECONDS

    require(timeoutNanos >= 0) { "Timeout too small" }

    return when {
      timeoutNanos < cutoff -> "${timeoutNanos}n"
      timeoutNanos < cutoff * 1_000L -> "${unit.toMicros(timeoutNanos)}u"
      timeoutNanos < cutoff * 1_000_000L -> "${unit.toMillis(timeoutNanos)}m"
      timeoutNanos < cutoff * 1_000_000_000L -> "${unit.toSeconds(timeoutNanos)}S"
      timeoutNanos < cutoff * 1_000_000_000L * 60L -> "${unit.toMinutes(timeoutNanos)}M"
      else -> "${unit.toHours(timeoutNanos)}H"
    }
  }

  fun parseAsciiString(serialized: String): Long {
    require(serialized.isNotEmpty()) { "empty timeout" }
    require(serialized.length <= 9) { "bad timeout format" }

    val value = serialized.dropLast(1).toLong()
    val unit = serialized.last()

    return when (unit) {
      'n' -> value
      'u' -> TimeUnit.MICROSECONDS.toNanos(value)
      'm' -> TimeUnit.MILLISECONDS.toNanos(value)
      'S' -> TimeUnit.SECONDS.toNanos(value)
      'M' -> TimeUnit.MINUTES.toNanos(value)
      'H' -> TimeUnit.HOURS.toNanos(value)
      else -> throw IllegalArgumentException("Invalid timeout unit: $unit")
    }
  }
}
