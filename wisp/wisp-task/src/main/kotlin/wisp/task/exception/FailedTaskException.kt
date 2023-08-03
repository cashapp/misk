package wisp.task.exception

import wisp.task.Status

/**
 * Can be thrown by a task to indicate it has failed.
 *
 * If a task returns [Status.FAILED], it is mapped to this exception and thrown.
 */
class FailedTaskException : Exception()
