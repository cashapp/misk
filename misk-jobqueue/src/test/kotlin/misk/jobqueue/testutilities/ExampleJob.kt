package misk.jobqueue.testutilities

internal data class ExampleJob(
  val color: Color,
  val message: String,
  val hint: ExampleJobHint? = null
)
