## misk-jooq

Seeks to integrate jooq with misk. Jooq is an open source project located here -
https://www.jooq.org/

In order to use jooq in your service you need to a few things:

1. Add this module as a dependency to your project.
1. You need to generate a jooq model from your database.
   Jooq's fluent API SQL generation depends on this generated model.
   There's more information on the jooq website here -
   https://www.jooq.org/doc/latest/manual/code-generation/

This is best done from running your migrations, and pointing
jooq-code-gen to it. There's an example of how to do this in this
module's build.gradle.kts. We are using it to generate a test db model in
order to test classes in this module.  
But the steps are here anyway:

a. Add the below lines to your build.gradle.kts

```kotlin
plugins {
  alias(libs.plugins.miskSchemaMigrator)
  alias(libs.plugins.jooq)
}

val schema = "<your service name>" // change this to your service name
val dbMigrations = "src/main/resources/db-migrations"
// We are using the schema migrator plugin here in order to run the migrations to create a schema.
// Ensure the migration directory is not called `migrations`. There's more details as to why below.
miskSchemaMigrator {
  database = schema
  username = "root"
  password = "root"
  host = "localhost"
  port = 3500
  migrationsDir.set(layout.projectDirectory.dir(dbMigrations))
}

// More details about the jooq plugin here - https://github.com/etiennestuder/gradle-jooq-plugin
jooq {
  edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

  configurations {
    create("main") {
      generateSchemaSourceOnCompilation.set(false)
      jooqConfiguration.apply {
        jdbc.apply {
          driver = "com.mysql.cj.jdbc.Driver"
          url = "jdbc:mysql://localhost:3500/$schema"
          user = "root"
          password = "root"
        }
        generator.apply {
          name = "org.jooq.codegen.KotlinGenerator"
          database.apply {
            name = "org.jooq.meta.mysql.MySQLDatabase"
            inputSchema = schema
            includes = ".*"
            excludes = "(.*?FLYWAY_SCHEMA_HISTORY)|(.*?schema_version)"
            recordVersionFields = "version"
          }
          generate.apply {
            isJavaTimeTypes = true
          }
          target.packageName = "<your service package name>"
        }
      }
    }
  }
}

// Needed to generate jooq test db classes
tasks.withType<nu.studer.gradle.jooq.JooqGenerate>().configureEach {
  dependsOn("migrateSchema")

  // declare migration files as inputs on the jOOQ task and allow it to
  // participate in build caching
  inputs.files(fileTree(layout.projectDirectory.dir(dbMigrations)))
    .withPropertyName("migrations")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  allInputsDeclared.set(true)
}
```

b. Have a look at `jooq-test-regenerate.sh`. Copy that into the root of your project and modify the database
name and docker container name.

c. Run `jooq-test-regenerate.sh` to have your model generated for you and ready to use.

d. Make sure to run `jooq-test-regenerate.sh` every time you add a migration.

## Examples of how to use this module

After wiring the `JooqModule` in like this

```kotlin
  install(JooqModule(
      qualifier = JooqDBIdentifier::class,
      dataSourceClusterConfig = datasourceConfig,
      jooqCodeGenSchemaName = "jooq",
      jooqTimestampRecordListenerOptions = JooqTimestampRecordListenerOptions(
        install = true,
        createdAtColumnName = "created_at",
        updatedAtColumnName = "updated_at"
      ),
      readerQualifier = JooqDBReadOnlyIdentifier::class
    ))
```

You can now start using the `JooqTransacter` anywhere like this

```kotlin
class Service @Inject constructor(
   @JooqDBIdentifier val transacter: JooqTransacter
) {
 
 // You can insert a row in a table like this
  ctx.newRecord(MOVIE).apply {
     this.genre = Genre.COMEDY.name
     this.name = "Enter the dragon"
     createdAt = clock.instant().toLocalDateTime()
     updatedAt = clock.instant().toLocalDateTime()
   }.also { it.store() }
   
// you can update a row like this
ctx.selectFrom(MOVIE)
   .where(BALANCE.ID.eq(id.id))
   .fetchOne()
   ?.apply { this.genre = Genre.COMEDY.name }
   ?.apply { updatedAt = clock.instant().toLocalDateTime() }
   ?.also { it.update() }    

// you can run complex queries just like writing normal sql. But the best part is
// you get compile time safety for incorrect queries!

ctx.select()
       .from(AUTHOR.join(BOOK)
          .on(BOOK.AUTHOR_ID.eq(AUTHOR.ID)))
       .where(BOOK.NAME.like("sum of all%")   
       .fetch()

//more info in the jooq website here - https://www.jooq.org/doc/latest/manual/ 
}

```

### Unified transacter

The `JooqUnifiedTransacterModule` sits on top of `JooqModule`. It provides a `misk.jooq.transacter.Transacter` interface
similar to that provided by `misk-jdbc` and `misk-hibernate`. Installing the module can be done as follows:

```kotlin
install(
    JooqUnifiedTransacterModule(
        writerQualifier = JooqDBIdentifier::class,
        readerQualifier = JooqDBReadOnlyIdentifier::class,
        // optional defaultTransacterOptions
        // optional transacterBindingQualifier
    )
)
```

Using the transacter can be done as follows:

```kotlin
class Service @Inject constructor(
    val transacter: Transacter
) {
    fun readOnlyExample() {
        // Reads from the writer but forbids writes
        transacter.readOnly().transaction { session ->
            session.ctx
                .selectFrom(AUTHOR)
                .fetch()
        }
    }

    fun replicaReadExample() {
        // Reads from the reader if a reader qualifier annotation was provided
        transacter.replicaRead { session ->
            session.ctx
                .selectFrom(AUTHOR)
                .fetch()
        }
    }

    fun mixingOptionsExample() {
        transacter.readOnly().noRetries().transaction {
            session.ctx
                .selectFrom(AUTHOR)
                .fetch()
        }
    }

    fun noRetriesExample() {
        transacter.noRetries().transaction { session ->
            session.ctx
                .selectFrom(AUTHOR)
                .fetch()
        }
    }

    fun moreRetriesExample() {
        transacter.maxAttempts(10).transaction { session ->
            session.ctx
                .selectFrom(AUTHOR)
                .fetch()
        }
    }
}

class MyServiceRpcClient constructor(
    val transacter: Transacter
) {
    fun makeRpc() {
        check(!transacter.inTransaction) { "Cannot make an RPC while holding a database connection" }
    }
}

```

## Future

1. Further, in order to use jooq we can't have migrations placed in a folder called `migrations`.
   The issue is jooq.jar ships with a directory called `migrations` with the some
   [migrations](https://github.com/jOOQ/jOOQ/tree/main/jOOQ/src/main/resources/migrations)
   we don't care about in it. When the service starts up it finds this folder as well and tries
   to run those migrations. Renaming misk service migrations to somethinq like `db-migrations` works. 

