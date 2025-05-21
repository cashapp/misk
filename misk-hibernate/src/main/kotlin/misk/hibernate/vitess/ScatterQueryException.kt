package misk.hibernate.vitess

import org.hibernate.JDBCException
import java.sql.SQLException

/**
 * Exception thrown if we use a scatter query that is too wide in the wrong context. This exception
 * will get thrown when scatter queries are disabled at the vtgate via the `no_scatter` flag.
 *
 * Consumers can opt in to using scatter queries via the `allow scatter` Vitess query hint. However,
 * we strongly discourage using scatter queries where possible because they do not scale well. A scatter
 * query is a query that hits all shards in a cluster. Imagine you have 128 shards. A 10k QPS operation
 * will fan out to all shards (and become a 1.28M QPS operation in totality), which can lead to MySQL
 * resource exhaustion and outages if your shards are not sized appropriately.
 *
 * Note: For eventually consistent reads (that go to replicas) scatter queries are more tolerable
 * because we can tune the availability by adding more replicas, though 2 replicas is often
 * used as a default.
 */
class ScatterQueryException(root: SQLException?) : JDBCException(
  "Scatter query detected. Must be opted-in through the `allow scatter` Vitess query hint.",
  root
)
