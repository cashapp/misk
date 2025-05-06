package misk.hibernate.vitess

import java.sql.SQLException
import org.hibernate.JDBCException

class PoolWaiterCountExhaustedException(root: SQLException?) : JDBCException(
  "Vitess pool waiter count exhausted",
  root
)
