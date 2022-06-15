package wisp.task.exception

/**
 * Thrown if an attempt to create a [RepeatedTask] that already exists with the name supplied.
 */
class TaskAlreadyExistsException(taskName: String) :
    Exception("Repeated Task with name: $taskName already exists!")
