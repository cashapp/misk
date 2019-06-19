package misk.hibernate.annotation

import misk.hibernate.Keyspace

@Target(AnnotationTarget.CLASS)
annotation class Keyspace(val value: String)

fun misk.hibernate.annotation.Keyspace.keyspace(): Keyspace = Keyspace(value)
