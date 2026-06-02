package misk.hibernate.vitess

import com.google.common.annotations.VisibleForTesting
import java.util.Optional
import java.util.regex.Matcher
import java.util.regex.Pattern
import misk.logging.getLogger
import misk.vitess.Shard

class VitessShardExceptionParser {
  /*
   * This method is used to check if the exception is a shard exception. It will also do a general
   * first level check on all causes to see if it's a shard health error and parse it out.
   *
   * Example exception message:
   * java.sql.SQLException: target: sharded_keyspaces.-40.primary: vttablet: rpc error: code =
   * Aborted desc = transaction 1729618751317964257: not found (CallerID: hsig4pj3doiu6hpew0jk)
   *
   * Returns: VitessShardException(sharded_keyspaces/-40)
   */
  fun parseShardInfo(exception: Exception): Optional<VitessShardExceptionData> {
    var vitessShardException: Optional<VitessShardExceptionData>
    var cause = exception.cause
    var message = exception.message

    vitessShardException = parseVitessShardException(exception, message!!)

    vitessShardException.ifPresent({ vitessShardException: VitessShardExceptionData ->
      this.logShardInfo(vitessShardException)
    })

    // Check if first error is a primary shard health error, if so we return
    if (isPrimaryShardAndContainsHealthError(vitessShardException)) {
      return vitessShardException
    }

    var i = 0
    while (cause != null && i < MAX_STACK_DEPTH) {
      message = cause.message
      if (message == null) {
        cause = cause.cause
        i++
        continue
      }

      vitessShardException = parseVitessShardException(cause, message)

      if (isPrimaryShardAndContainsHealthError(vitessShardException)) {
        vitessShardException.ifPresent({ vitessShardException: VitessShardExceptionData ->
          this.logShardInfo(vitessShardException)
        })
        return vitessShardException
      }

      cause = cause.cause
      i++
    }
    if (vitessShardException.isPresent())
      vitessShardException.ifPresent({ vitessShardException: VitessShardExceptionData ->
        this.logShardInfo(vitessShardException)
      })

    return vitessShardException
  }

  private fun parseVitessShardException(exception: Throwable, message: String): Optional<VitessShardExceptionData> {
    val shardString = getShardString(message)
    val isShardHealthError = isShardHealthErrorCheck(message)

    if (shardString.isNotEmpty()) {
      val isPrimary = message.contains(".primary:")
      val shard: Shard = Shard.parse(shardString)!!

      return Optional.of<VitessShardExceptionData>(
        VitessShardExceptionData(shard, message, isShardHealthError, isPrimary, exception)
      )
    }
    return Optional.empty<VitessShardExceptionData>()
  }

  private fun isPrimaryShardAndContainsHealthError(shardException: Optional<VitessShardExceptionData>): Boolean {
    if (shardException.isPresent()) {
      val exception: VitessShardExceptionData = shardException.get()
      return exception.isPrimary && exception.isShardHealthError
    }
    return false
  }

  private fun isShardHealthErrorCheck(message: String): Boolean {
    // Check if message contains at least one included pattern
    return INCLUDED_MESSAGES.stream().anyMatch { pattern: String? -> Pattern.compile(pattern).matcher(message).find() }
  }

  private fun getShardString(message: String): String {
    val matcher = SHARD_PATTERN.matcher(message)
    return if (matcher.find()) parseShardString(matcher) else ""
  }

  /*
   * This method is used to parse the shard information from the exception message.
   *
   * Example exception message:
   * java.sql.SQLException: target: sharded_keyspaces.-40.primary: vttablet: rpc error: code = A
   * borted desc = transaction 1729618751317964257: not found (CallerID: hsig4pj3doiu6hpew0jk)
   *
   * Returns: sharded_keyspaces/-40
   */
  private fun parseShardString(matcher: Matcher): String {
    // Group 1 is the keyspace (sharded_keyspaces)
    // Group 2 is the shard (-40, 40-80, ff-, de-df, etc.)
    val keyspace = matcher.group(1)
    val shard = matcher.group(2)

    return "$keyspace/$shard"
  }

  private fun logShardInfo(vitessShardException: VitessShardExceptionData) {
    logger.warn(
      ("""vitessShardException: ${vitessShardException.shard}
        | message: ${vitessShardException.causeException.message}
        |  isprimary:${vitessShardException.isPrimary}
        |   isShardHealthError: ${vitessShardException.isShardHealthError}"""
        .trimMargin())
    )
  }

  @VisibleForTesting
  fun configureStackDepth(maxStackDepth: Int) {
    MAX_STACK_DEPTH = maxStackDepth
  }

  companion object {
    private var MAX_STACK_DEPTH = 300 // Making the stack depth intentionally high to avoid missing root cause

    private val SHARD_PATTERN: Pattern =
      Pattern.compile(
        "target:\\s+([^.]+)\\.(-?[0-9]+|[0-9a-f]+-[0-9a-f]+|[0-9]+(?:-[0-9]+)?|[0-9a-f]+-|[0-9a-f]+)\\.(primary|replica):"
      )

    private val INCLUDED_MESSAGES =
      listOf(
        "due to context deadline exceeded",
        "primary is not serving",
        "code = Aborted desc = transaction.*not found",
        "code = Aborted desc = transaction.*ended at.*\\(unlocked closed connection\\)",
        "operation not allowed in state NOT_SERVING",
      )

    private val logger = getLogger<VitessShardExceptionParser>()
  }
}
