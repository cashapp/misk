package misk.hibernate

@Target(AnnotationTarget.FIELD)
annotation class SecretColumnWithAad(val keyName: String)