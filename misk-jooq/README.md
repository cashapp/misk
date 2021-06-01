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

```
buildscript {
  dependencies {
    classpath("gradle.plugin.com.boxfuse.client:flyway-release:5.0.2")
    classpath(Dependencies.mysql)
  }
}
plugins {
  id("org.flywaydb.flyway") version "5.2.4"
  id("nu.studer.jooq") version "5.2"
}
// We are using flyway here in order to run the migrations to create a schema. 
// Ensure the migration directory is not called `migrations`. There's more details as to why below.
flyway {
  url = "jdbc:mysql://localhost:3500/misk-jooq-test-codegen"
  user = "root"
  password = "root"
  schemas = arrayOf("jooq")
  locations = arrayOf("filesystem:${project.projectDir}/src/main/resources/db-migrations")
  sqlMigrationPrefix = "v"
}
// More details about the jooq plugin here - https://github.com/etiennestuder/gradle-jooq-plugin
jooq {
  version.set("3.14.8")
  edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

  configurations {
    create("main") {
      generateSchemaSourceOnCompilation.set(false)
      jooqConfiguration.apply {
        jdbc.apply {
          driver = "com.mysql.cj.jdbc.Driver"
          url = "jdbc:mysql://localhost:3500/misk-jooq-test-codegen" // change this to your service name
          user = "root"
          password = "root"
        }
        generator.apply {
          name = "org.jooq.codegen.KotlinGenerator"
          database.apply {
            name = "org.jooq.meta.mysql.MySQLDatabase"
            inputSchema = "jooq"
            outputSchema = "jooq"
            includes = ".*"
            excludes = "(.*?FLYWAY_SCHEMA_HISTORY)|(.*?schema_version)"
            recordVersionFields = "version"
          }
          generate.apply {
            isJavaTimeTypes = true
          }
          target.apply {
            packageName = "<your service package name>"
            directory   = "${project.projectDir}/src/main/generated/kotlin"
          }
        }
      }
    }
  }
}
val generateJooq by project.tasks
generateJooq.dependsOn("flywayMigrate")

sourceSets.getByName("main").java.srcDirs
  .add(File("${project.projectDir}/src/main/generated/kotlin"))
```

b. Have a look at `jooq-test-regenerate.sh`. Copy that into the root of your project and modify the database 
name and docker container name.

c. Run `jooq-test-regenerate.sh` to have your model generated for you and ready to use.

d. Make sure to run `jooq-test-regenerate.sh` every time you add a migration.


## Examples of how to use this module

After wiring the `JooqModule` in like this

```
install(JooqModule(<qualifier>, datasourceConfig, <reader qualifier>))
```

You can now start using the `JooqTransacter` anywhere like this  

```
class Service @Inject constructor(
   @JooqDBIdentifier val transacter: JooqTransacter\
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

//more info in the joow website here - https://www.jooq.org/doc/latest/manual/ 
}

```



## Future 

1. The generation tool uses flyway to generate the schema. I would like to use misk-jdbc's 
   SchemaMigrationService built into a gradle plugin. That same plugin can also generate the 
   jooq classes.  
   Further, in order to use jooq we can't have migrations placed in a folder called `migrations`. 
   The issue is jooq.jar is shipped with a directory called `migrations` with the some 
   [migrations](https://github.com/jOOQ/jOOQ/tree/main/jOOQ/src/main/resources/migrations) 
   we don't care about in it. When the service starts up it finds this folder as well and tries 
   to run those migrations. Renaming misk service migrations to somethinq like `db-migrations` works. 
   