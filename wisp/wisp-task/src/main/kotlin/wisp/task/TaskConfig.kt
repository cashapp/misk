package wisp.task

import wisp.config.Config

/**
 * Config to pass to the task.  Extend this to pass in anything else your task may need.
 *
 * Note that this extends [Config], so you can load your task's config if required.
 */
open class TaskConfig: Config
