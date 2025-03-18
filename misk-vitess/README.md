# Vitess

This module aims to encapsulate useful constructs for working with Vitess. It currently includes `VitessTestDb`, a construct that enables developers to run tests against a Vitess database.

## What is Vitess?

[Vitess](https://vitess.io) is an open-source platform to manage horizontally scaling MySQL. It originally started at YouTube, and is used today by companies with high SQL scaling needs such as Slack, GitHub, Shopify, HubSpot, Etsy, PlanetScale (as a DB platform), Block (i.e. Cash App and Square), and many others. Block historically managed its own Vitess infrastructure and makes open source contributions to https://github.com/vitessio/vitess.

## VitessTestDb

**VitessTestDb** enables developers to run their tests against a Vitess environment that mimics staging and production. This enables validating that queries work as expected in an environment that supports routing queries to shards, a Vschema, and Vitess query types such as cross shard queries, scatters, and lookups. VitessTestDb has been in use by Block for years to run over 100,000 tests in CI daily across many apps.

**VitessTestDb** uses `Docker`, `vttestserver`, and `vtctldclient` to simplify Vitess database operations such as container start-up, schema change management, sequence table initialization, and table truncation. Schema changes can be applied without requiring a time-consuming container restart. It offers configuration knobs like the Vitess image version, MySQL version, transaction isolation level, disabling scatter queries, and more. It also offers Vschema linting and schema validation to ensure that the schema is correct before starting the test database.

### Usage

Starting a local Vitess test database in tests is as simple as:

```
val testDb = VitessTestDb()
testDb.run()
```

This will start a minified Vitess cluster in a Docker container that will be ready to accept connections on port 27003.

### Configuration

`VitessTestDb`  depends on the existence of a schema directory ( i.e., `schemaDir`),
which contains `.sql` schema change and `vschema.json` files.  The expected input format is shown below:

```
/vitess/schema
├── keyspace1
│   ├── v0001__add_table.sql
│   ├── vschema.json
├── keyspace2
│   ├── v0002__add_table2.sql
│   ├── vschema.json
```

The `schemaDir` option defaults to `vitess/schema`, but can take in any custom resource path to the constructor, e.g.,

```
testDb = VitessTestDb(schemaDir = "prefix/my_schema")
```

Other configurable parameters include:
- `autoApplySchema` (default `true`): Whether to apply the schema on start-up. If set to `false`, the schema can be applied at run-time via `applySchema()` or externally.
- `containerName` (default `vitess_test_db`): The name assigned to the Docker container that gets created.
- `debugStartup` (default `false`): Whether to print debug logs during the startup process.
- `enableDeclarativeSchemaChanges` (default `false`): Whether to use declarative schema changes.
- `enableScatters` (default `true`): Whether to enable scatter queries, which are queries that fan out to all shards. Requires a Vitess image version >= 20.
- `keepAlive` (default `true`): Whether to keep the container running between test runs. It will detect schema changes and argument changes and restart the container if needed.
- `lintSchema` (default `false`): Whether to lint the schema before starting the test database, which can help detect errors early.
- `mysqlVersion` (default `8.0.36`): The MySQL version to use.
- `port` (default `27003`): The port used to connect to the sharded database, aka the vtgate.
- `sqlMode` (defaults to the [MySQL8 defaults](https://dev.mysql.com/doc/refman/8.0/en/sql-mode.html)): The server side `sql_mode` setting.
- `transactionIsolationLevel` (default `REPEATABLE_READ`): The transaction isolation level.
- `transactionTimeoutSeconds` (default `30s`): the duration in seconds before Vitess times out a transaction. Setting a higher value may be useful for debugging.
- `vitessImage` (default: `vitess/vttestserver:v19.0.9-mysql80`): The Docker image to use for the container. DockerHub Images can be  found at https://hub.docker.com/r/vitess/vttestserver/tags. Custom ECR images can also be passed in.
- `vitessVersion` (default: `19`): The version of Vitess to use, which must match the version of `vitessImage`.

### Connecting to the test database
After you start the test database, you can connect to it via a standard connection or through the MySQL CLI.

####  App connection

You can connect your Misk app using a standard DataSource config for development:
```
data_source_clusters:
  service_name:
    writer:
      type: VITESS_MYSQL
      database: "@primary"
      username: root
      password: ""
      port: 27003
```

For more primitive forms of testing, you can also use raw JDBC:
```
val url = "jdbc:mysql://localhost:27003/@primary"
val user = "root"
val password = ""
val connection = DriverManager.getConnection(url, user, password)
    
val statement: Statement = connection.createStatement()
val resultSet = statement.executeQuery("select * from my_table")
```

#### MySQL CLI
With the MySQL CLI, you can connect using the following command:

```
mysql -h 127.0.0.1 -P 27003 -u root --skip-binary-as-hex
```

The `--skip-binary-as-hex` flag is added to make hex strings in the output readable, which may show up in some Vitess tables.
