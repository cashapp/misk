package misk.hibernate.vitess

import misk.vitess.Keyspace
import misk.vitess.Shard
import org.assertj.core.api.Assertions
import org.hibernate.JDBCException
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.GenericJDBCException
import org.hibernate.exception.spi.SQLExceptionConversionDelegate
import org.junit.jupiter.api.Test
import java.sql.SQLException

class VitessDialectTest {
  private val vitessDialect: VitessDialect = VitessDialect()

  @Test fun testBuildSQLExceptionConversionDelegate_DuplicateEntry() {
    val sqlException = SQLException("Duplicate entry")

    val delegate: SQLExceptionConversionDelegate =
      vitessDialect.buildSQLExceptionConversionDelegate()

    Assertions.assertThatThrownBy {
      throw delegate.convert(
        sqlException,
        "Duplicate entry",
        "INSERT INTO table (id) VALUES (1)"
      )
    }.isInstanceOf(ConstraintViolationException::class.java)
      .hasMessageContaining("Duplicate entry")
  }

  @Test fun testBuildSQLExceptionConversionDelegate_WaiterPoolExhausted() {
    val sqlException = SQLException("pool waiter count exceeded")

    val delegate: SQLExceptionConversionDelegate =
      vitessDialect.buildSQLExceptionConversionDelegate()

    Assertions.assertThat<JDBCException>(
      delegate.convert(
        sqlException,
        "pool waiter count exceeded",
        "SELECT * FROM table"
      )
    )
      .isInstanceOf(PoolWaiterCountExhaustedException::class.java)
  }

  @Test fun testBuildSQLExceptionConversionDelegate_OtherException() {
    val sqlException = SQLException("Some other SQL exception")

    val delegate: SQLExceptionConversionDelegate =
      vitessDialect.buildSQLExceptionConversionDelegate()

    Assertions.assertThat(
      delegate.convert(
        sqlException,
        "Some other SQL exception",
        "UPDATE table SET column = value"
      )
    )
      .isInstanceOf(GenericJDBCException::class.java)
  }

  @Test fun testBuildSQLExceptionConversionDelegate_VitessShardException() {
    val cause = Exception(
      ("target: sharded_keyspace.80-.primary: vttablet: "
        + "rpc error: code = Aborted desc = transaction 1729618751317964257: not found "
        + "(CallerID: hsig4pj3doiu6hpew0jk)")
    )
    val genericExceptionOne = Exception("Database error", cause)
    val genericExceptionTwo = Exception("Database error", genericExceptionOne)
    val genericExceptionThree = Exception("Database error", genericExceptionTwo)
    val genericExceptionFour = Exception("Database error", genericExceptionThree)
    val genericExceptionFive = Exception("Database error", genericExceptionFour)

    val exceptionData: VitessShardExceptionData =
      VitessShardExceptionData(
        Shard(
          Keyspace("sharded_keyspace"),
          "80-"
        ),
        cause.message.toString(),
        true,
        true,
        cause
      )

    val delegate: SQLExceptionConversionDelegate =
      vitessDialect.buildSQLExceptionConversionDelegate()

    Assertions.assertThatThrownBy {
      throw delegate.convert(
        SQLException(genericExceptionFive),
        "Shard exception message",
        "SELECT * FROM table"
      )
    }.isInstanceOf(VitessShardException::class.java)
      .hasMessageContaining(exceptionData.exceptionMessage)
      .satisfies({ exception: Throwable ->
        val shardException: VitessShardException = exception as VitessShardException
        val actualData: VitessShardExceptionData = shardException.exceptionData
        Assertions.assertThat(actualData.shard).isEqualTo(exceptionData.shard)
        Assertions.assertThat(actualData.isShardHealthError).isEqualTo(exceptionData.isShardHealthError)
        Assertions.assertThat(actualData.isPrimary).isEqualTo(exceptionData.isPrimary)
        Assertions.assertThat(actualData.exceptionMessage).isEqualTo(exceptionData.exceptionMessage)
      })
  }

  @Test fun testBuildSQLExceptionConversionDelegate_VitessShardExceptionRowCount() {
    val cause = Exception(
      ("target: sharded_keyspace.50-58.primary: vttablet: "
        + "rpc error: code = Aborted desc = "
        + "Row count exceeded 10000 (CallerID: b9j9lbqsjgrgv8eg7a42)")
    )

    val genericExceptionOne = Exception("Database error", cause)
    val genericExceptionTwo = Exception("Database error", genericExceptionOne)
    val genericExceptionThree = Exception("Database error", genericExceptionTwo)
    val genericExceptionFour = Exception("Database error", genericExceptionThree)
    val genericExceptionFive = Exception("Database error", genericExceptionFour)

    val exceptionData: VitessShardExceptionData =
      VitessShardExceptionData(
        Shard(
          Keyspace("sharded_keyspace"),
          "50-58"
        ),
        cause.message.toString(),
        false,
        true,
        cause
      )

    val delegate: SQLExceptionConversionDelegate =
      vitessDialect.buildSQLExceptionConversionDelegate()

    Assertions.assertThatThrownBy {
      throw delegate.convert(
        SQLException(genericExceptionFive),
        "Shard exception message",
        "SELECT * FROM table"
      )
    }.isInstanceOf(VitessShardException::class.java)
      .hasMessageContaining(exceptionData.exceptionMessage)
      .satisfies({ exception: Throwable? ->
        val shardException: VitessShardException = exception as VitessShardException
        val actualData: VitessShardExceptionData = shardException.exceptionData
        Assertions.assertThat(actualData.shard).isEqualTo(exceptionData.shard)
        Assertions.assertThat(actualData.isShardHealthError).isEqualTo(exceptionData.isShardHealthError)
        Assertions.assertThat(actualData.isPrimary).isEqualTo(exceptionData.isPrimary)
        Assertions.assertThat(actualData.exceptionMessage).isEqualTo(exceptionData.exceptionMessage)
      })
  }
}
