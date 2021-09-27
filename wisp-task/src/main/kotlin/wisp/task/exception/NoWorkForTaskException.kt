package wisp.task.exception

import wisp.task.Status

/**
 * Can be thrown by a task to indicate it has no work to do at this time.
 *
 * If a task returns [Status.NO_WORK], it is mapped to this exception and thrown.
 */
class NoWorkForTaskException : Exception()
