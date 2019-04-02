package misk.hibernate

@Target(AnnotationTarget.FIELD)
annotation class EncryptedColumn(val keyName: String)