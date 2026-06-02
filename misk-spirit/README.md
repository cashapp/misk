# misk-spirit

Wrapper around the [Spirit](https://github.com/block/spirit) CLI. Spirit is an online schema change and data operations tool for MySQL 8.0+.

This module uses Spirit's `diff` subcommand to compare a live MySQL database against `.sql` files and generate the DDL statements needed to bring the database in sync.

## Usage

```kotlin
val spirit = Spirit()
val dsn = "user:pass@tcp(host:3306)/mydb"
val sqlFiles = mapOf("users.sql" to "CREATE TABLE users (id BIGINT NOT NULL, PRIMARY KEY (id));")

val result = spirit.diff(dsn, sqlFiles)
if (result.hasDiff) {
  println(result.diff) // DDL statements to apply
}
```

## Installation

Spirit must be available on the system. Install via Homebrew:

```sh
brew install block/tap/spirit
```

Or via [Hermit](https://cashapp.github.io/hermit/):

```sh
hermit install spirit
```
