package wisp.task.exception

/**
 * Thrown if an attempt to retrieve a non-existent [RepeatedTask].
 */
class NoTaskFoundException(taskName: String) :
    Exception("No Repeated Task with name: $taskName found!")
