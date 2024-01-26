package misk.jobqueue.testutilities

internal enum class ExampleJobHint {
  DONT_ACK,
  THROW,
  THROW_ONCE,
  DEAD_LETTER,
  DEAD_LETTER_ONCE
}
