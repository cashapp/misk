This module provides a DynamoDb backed implementation of the misk-clustering
Cluster interface. At a high level, we simply record every N seconds that we
are still alive, and we cache all entries from the table that are within Y
seconds of freshness.

## Installation

1. Install `DynamoClusterModule`
2. Optionally modify the `DynamoClusterConfig` configuration
3. Set up your DynamoDb table like so:

```
name = "misk-cluster-members"
kind = "dynamodbv2"
hash_key = "name"
ttl_attribute = "expires_at"

attribute {
  name = "name"
  type = "S"
}
```
