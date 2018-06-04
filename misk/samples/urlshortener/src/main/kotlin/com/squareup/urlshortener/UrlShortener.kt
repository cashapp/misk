package com.squareup.urlshortener

import javax.inject.Qualifier

/** Identifies the urlshortener's DB cluster. */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class UrlShortener