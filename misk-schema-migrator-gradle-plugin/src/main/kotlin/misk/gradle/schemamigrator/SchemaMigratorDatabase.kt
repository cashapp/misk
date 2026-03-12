package misk.gradle.schemamigrator

import jakarta.inject.Qualifier

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class SchemaMigratorDatabase
