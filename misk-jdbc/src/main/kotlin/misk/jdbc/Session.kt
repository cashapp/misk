package misk.jdbc

import java.sql.Connection

interface Session {
  fun <T> useConnection(work: (Connection) -> T): T
  /**
   * Registers a hook that fires after the session transaction commits. Post-commit hooks cannot
   * affect the disposition of the transaction; if a post-commit hook fails, the failure
   * will be logged but not propagated to the application, as the transaction will have already
   * committed
   */
  fun onPostCommit(work: () -> Unit)

  /**
   * Registers a hook that fires before the session's transaction commits. Failures in a pre-commit
   * hook will cause the transaction to be rolled back.
   */
  fun onPreCommit(work: () -> Unit)

  /**
   * Registers a hook that fires after a session is closed. This is called regardless if a session
   * was successfully committed or rolled back.
   *
   * A new transaction can be initiated as part of this hook.
   */
  fun onSessionClose(work: () -> Unit)
}
