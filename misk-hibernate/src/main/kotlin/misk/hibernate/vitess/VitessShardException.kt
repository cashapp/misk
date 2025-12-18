package misk.hibernate.vitess

import java.sql.SQLException
import org.hibernate.JDBCException

/**
 * Custom exception indicating an error related to a specific Vitess shard operation. Wraps details provided by
 * [VitessShardExceptionData].
 */
class VitessShardException(
  /** Holds detailed information about the Vitess shard error. */
  val exceptionData: VitessShardExceptionData
) :
  JDBCException(
    // Construct the message for the superclass
    "Vitess Shard Error: ${exceptionData.exceptionMessage}",
    // Construct the SQLException cause for the superclass
    SQLException(exceptionData.causeException),
  )
