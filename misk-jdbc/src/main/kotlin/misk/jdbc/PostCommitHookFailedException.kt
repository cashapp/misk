package misk.jdbc;

/**
 * [PostCommitHookFailedException] is raised when a code run as part of a post-commit hook
 * fails. Because post-commit hooks are run after the transaction is committed, failure in these
 * hooks does not cause the transaction to rollback, and applications may need to differentiate
 * the two cases (exception occurred and caused the transaction to rollback, exception
 * occurred during a post-commit hook
 *
 */
class PostCommitHookFailedException(cause: Throwable) : Exception(cause)