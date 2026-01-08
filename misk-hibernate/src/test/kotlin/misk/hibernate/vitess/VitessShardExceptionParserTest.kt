package misk.hibernate.vitess

import java.sql.SQLException
import java.util.Optional
import org.assertj.core.api.Assertions
import org.hibernate.exception.GenericJDBCException
import org.junit.jupiter.api.Test

class VitessShardExceptionParserTest {
  private val parser: VitessShardExceptionParser = VitessShardExceptionParser()

  @Test
  fun parseShardInfo_withSqlException_returnsShard() {
    val sqlException = SQLException(SQL_EXCEPTION_MESSAGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo(EXPECTED_SHARD_ID)
  }

  @Test
  fun parseShardInfo_withGenericJDBCException_returnsShard() {
    val cause = SQLException(SQL_EXCEPTION_MESSAGE)
    val genericException = GenericJDBCException("Database error", cause)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo(EXPECTED_SHARD_ID)
  }

  @Test
  fun parseShardInfo_withNonShardException_returnsEmpty() {
    val nonShardException = Exception("Some other error")

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(nonShardException)

    Assertions.assertThat(result).isEmpty()
  }

  @Test
  fun parseShardInfo_withSqlExceptionNoShardInfo_returnsEmpty() {
    val sqlException = SQLException("Generic database error")

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isEmpty()
  }

  @Test
  fun parseShardInfo_withGenericJDBCExceptionNoShardInfo_returnsEmpty() {
    val cause = SQLException("Generic database error")
    val genericException = GenericJDBCException("Database error", cause)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericException)

    Assertions.assertThat(result).isEmpty()
  }

  @Test
  fun parseShardInfo_withNullCause_returnsEmpty() {
    val genericException = GenericJDBCException("Database error", null)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericException)

    Assertions.assertThat(result).isEmpty()
  }

  @Test
  fun parseShardInfo_withMalformedShardString_returnsEmpty() {
    val sqlException = SQLException("target: malformed.shard.string: vttablet:")

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isEmpty()
  }

  @Test
  fun parseShardInfo_withRangeShardFormat_returnsShard() {
    val sqlException = SQLException(SQL_EXCEPTION_MESSAGE_RANGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/40-80")
  }

  @Test
  fun parseShardInfo_withGenericJDBCExceptionRangeFormat_returnsShard() {
    val cause = SQLException(SQL_EXCEPTION_MESSAGE_RANGE)
    val genericException = GenericJDBCException("Database error", cause)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/40-80")
  }

  @Test
  fun parseShardInfo_withOpenRangeShardFormat_returnsShard() {
    val sqlException = SQLException(SQL_EXCEPTION_MESSAGE_OPEN_RANGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/80-")
  }

  @Test
  fun parseShardInfo_withGenericJDBCExceptionOpenRangeFormat_returnsShard() {
    val cause = SQLException(SQL_EXCEPTION_MESSAGE_OPEN_RANGE)
    val genericException = GenericJDBCException("Database error", cause)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/80-")
  }

  @Test
  fun parseShardInfo_withSixNestedGenericJDBCExceptionOpenRangeFormat_returnsShard() {
    val cause = Exception(SQL_EXCEPTION_MESSAGE_OPEN_RANGE)
    val genericExceptionOne = Exception("Database error", cause)
    val genericExceptionTwo = Exception("Database error", genericExceptionOne)
    val genericExceptionThree = Exception("Database error", genericExceptionTwo)
    val genericExceptionFour = Exception("Database error", genericExceptionThree)
    val genericExceptionFive = Exception("Database error", genericExceptionFour)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericExceptionFive)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/80-")
  }

  @Test
  fun parseShardInfo_withDeadlineExceeded_returnsShard() {
    val sqlException = SQLException(DEADLINE_EXCEEDED_MESSAGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/-40")
  }

  @Test
  fun parseShardInfo_withNotServing_returnsShard() {
    val sqlException = SQLException(NOT_SERVING_MESSAGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/-40")
  }

  @Test
  fun parseShardInfo_withTransactionEnded_returnsShard() {
    val sqlException = SQLException(TRANSACTION_ENDED_MESSAGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/-40")
  }

  @Test
  fun parseShardInfo_withNoShardInfoTransactionEnded_returnsEmpty() {
    val sqlException = SQLException(NO_SHARD_INFO_TRANSACTION_ENDED_MESSAGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isEmpty()
  }

  @Test
  fun parseShardInfo_withGenericJDBCExceptionNoShardInfoTransactionEnded_returnsEmpty() {
    val cause = SQLException(NO_SHARD_INFO_TRANSACTION_ENDED_MESSAGE)
    val genericException = GenericJDBCException("Database error", cause)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericException)

    Assertions.assertThat(result).isEmpty()
  }

  @Test
  fun parseShardInfo_withNotServingState_returnsShard() {
    val sqlException = SQLException(NOT_SERVING_STATE_MESSAGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/-40")
  }

  @Test
  fun parseShardInfo_withGenericJDBCExceptionNotServingState_returnsShard() {
    val cause = SQLException(NOT_SERVING_STATE_MESSAGE)
    val genericException = GenericJDBCException("Database error", cause)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericException)

    Assertions.assertThat(result).isPresent()
    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/-40")
  }

  @Test
  fun parseShardInfo_withExcludedMessage_emitsMetricReturnsRowCountExceeded() {
    val excludedMessage = "target: sharded_keyspace.-40.primary: vttablet: Row count exceeded 10000"
    val sqlException = SQLException(excludedMessage)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    // Verify result is empty
    Assertions.assertThat(result).isNotEmpty()

    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/-40")
    Assertions.assertThat(result.get().isPrimary).isEqualTo(true)
  }

  @Test
  fun parseShardInfo_withReplicaTransactionEnded_emitsMetrics() {
    val sqlException = SQLException(REPLICA_TRANSACTION_ENDED_MESSAGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    // Verify result is empty
    Assertions.assertThat(result).isNotEmpty()

    Assertions.assertThat(result.get().shard.toString()).isEqualTo("sharded_keyspace/-40")
    Assertions.assertThat(result.get().isPrimary).isEqualTo(false)
    Assertions.assertThat(result.get().isShardHealthError).isEqualTo(true)
  }

  @Test
  fun parseShardInfo_withDuplicateShardPatterns_emitsMetricOnce() {
    // Create exception with same shard pattern repeated
    val cause = SQLException(SQL_EXCEPTION_MESSAGE)
    val exception = GenericJDBCException(SQL_EXCEPTION_MESSAGE, cause)

    parser.parseShardInfo(exception)
  }

  @Test
  fun parseShardInfo_withSqlException_returnsVitessShardExceptionData() {
    val sqlException = SQLException(SQL_EXCEPTION_MESSAGE)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(sqlException)

    Assertions.assertThat(result).isPresent()
    val exception: VitessShardExceptionData = result.get()
    Assertions.assertThat(exception.shard.toString()).isEqualTo(EXPECTED_SHARD_ID)
    Assertions.assertThat(exception.isShardHealthError).isTrue()
    Assertions.assertThat(exception.isPrimary).isTrue()
    Assertions.assertThat(exception.exceptionMessage).isEqualTo(SQL_EXCEPTION_MESSAGE)
  }

  @Test
  fun parseShardInfo_withGenericJDBCException_returnsVitessShardExceptionData() {
    val cause = SQLException(SQL_EXCEPTION_MESSAGE)
    val genericException = GenericJDBCException("Database error", cause)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericException)

    Assertions.assertThat(result).isPresent()
    val exception: VitessShardExceptionData = result.get()
    Assertions.assertThat(exception.shard.toString()).isEqualTo(EXPECTED_SHARD_ID)
    Assertions.assertThat(exception.isShardHealthError).isTrue()
    Assertions.assertThat(exception.isPrimary).isTrue()
    Assertions.assertThat(exception.exceptionMessage).isEqualTo(SQL_EXCEPTION_MESSAGE)
  }

  @Test
  fun parseShardInfo_withResultSetExtractionError_doesNotFindNestedShardError() {
    parser.configureStackDepth(118)
    val genericException = createNestedException("Outer exception", NOT_SERVING_STATE_MESSAGE, 119)

    val result: Optional<VitessShardExceptionData> = parser.parseShardInfo(genericException)

    Assertions.assertThat(result).isNotPresent()
  }

  companion object {
    private const val SQL_EXCEPTION_MESSAGE =
      ("target: sharded_keyspace.-40.primary: vttablet: " +
        "rpc error: code = Aborted desc = transaction 1729618751317964257: not found " +
        "(CallerID: hsig4pj3doiu6hpew0jk)")

    private const val SQL_EXCEPTION_MESSAGE_RANGE =
      ("target: sharded_keyspace.40-80.primary: vttablet: " +
        "rpc error: code = Aborted desc = transaction 1729618751317964257: not found " +
        "(CallerID: hsig4pj3doiu6hpew0jk)")

    private const val SQL_EXCEPTION_MESSAGE_OPEN_RANGE =
      ("target: sharded_keyspace.80-.primary: vttablet: " +
        "rpc error: code = Aborted desc = transaction 1729618751317964257: not found " +
        "(CallerID: hsig4pj3doiu6hpew0jk)")

    private const val EXPECTED_SHARD_ID = "sharded_keyspace/-40"

    private const val DEADLINE_EXCEEDED_MESSAGE =
      ("target: sharded_keyspace.-40.primary: vttablet: (errno 2013) due to context deadline exceeded, " +
        "elapsed time: 30.000905206s, killing query ID 8929 (CallerID: hsig4pj3doiu6hpew0jk)")

    private const val NOT_SERVING_MESSAGE =
      ("target: sharded_keyspace.-40.primary: primary is not serving, " + "there is a reparent operation in progress")

    private const val TRANSACTION_ENDED_MESSAGE =
      ("target: sharded_keyspace.-40.primary: vttablet: rpc error: code = Aborted desc = " +
        "transaction 1729618751317963616: ended at 2024-10-22 21:22:21.227 UTC " +
        "(unlocked closed connection) (CallerID: hsig4pj3doiu6hpew0jk)")

    private const val REPLICA_TRANSACTION_ENDED_MESSAGE =
      ("target: sharded_keyspace.-40.replica: vttablet: rpc error: code = Aborted desc = " +
        "transaction 1729618751317963616: ended at 2024-10-22 21:22:21.227 UTC " +
        "(unlocked closed connection) (CallerID: hsig4pj3doiu6hpew0jk)")

    private const val NO_SHARD_INFO_TRANSACTION_ENDED_MESSAGE =
      ("target: f.primary: vttablet: rpc error: code = Aborted desc = " +
        "transaction 1729618751317963616: ended at 2024-10-22 21:22:21.227 UTC " +
        "(unlocked closed connection) (CallerID: hsig4pj3doiu6hpew0jk)")

    private const val REPLICA_EXCLUDED_MESSAGE =
      ("target: sharded_keyspace.-40.replica: vttablet: rpc error: code = Aborted desc = " + "Row count exceeded 10000")

    private const val NOT_SERVING_STATE_MESSAGE =
      "target: sharded_keyspace.-40.primary: operation not allowed in state NOT_SERVING"

    /**
     * Creates a nested exception chain with the specified depth. The SQLException with the Vitess error message is at
     * the bottom of the stack. All other exceptions are generic SQLExceptions.
     *
     * @param outerMessage The message for the outer exceptions
     * @param sqlErrorMessage The Vitess error message for the bottom SQLException
     * @param depth The number of nested exceptions to create
     * @return The outermost exception containing the nested chain
     */
    private fun createNestedException(outerMessage: String, sqlErrorMessage: String, depth: Int): Exception {
      // Create the bottom SQLException first with the Vitess error message
      val cause = SQLException(sqlErrorMessage)
      var exceptionStack = SQLException("first", cause)
      // Add GenericJDBCException layers on top
      for (i in 1..depth) {
        exceptionStack = SQLException("Layer " + (depth - i + 1) + " - " + outerMessage, exceptionStack)
      }
      return exceptionStack
    }
  }
}
