package misk.hibernate

import jakarta.inject.Qualifier

@Qualifier @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION) annotation class Movies

@Qualifier @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION) annotation class MoviesReader
