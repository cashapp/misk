## misk-jooq

Seeks to integrate jooq with misk. Jooq is an open source project located here -
https://www.jooq.org/

In order to use jooq in your service you need to a few things:

1. Add this module as a dependency to your project.
1. You need to generate a jooq model from your database. 
This is best done from running your migrations, and pointing 
jooq-code-gen to it. There's an example of how to do this in this 
   module's build.gradle.kts. We are using it to generate a test db model in 
   order to test classes in this module.  
   But I'll walk through the steps here anyway:
   
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
flyway {
  url = "jdbc:mysql://localhost:3500/misk-jooq-test-codegen"
  user = "root"
  password = "root"
  schemas = arrayOf("jooq")
  locations = arrayOf("filesystem:${project.projectDir}/src/main/resources/db-migrations")
  sqlMigrationPrefix = "v"
}
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
            packageName = "app.cash.backfila.client.misk.jooq.gen"
            directory   = "${project.projectDir}/src/test/generated/kotlin"
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



## Future 

1. The generation tool uses flyway to generate the schema. I would like to use misk-jdbc's 
   SchemaMigrationService built into a gradle plugin. That same plugin can also generate the 
   jooq classes.