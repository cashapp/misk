package misk.hibernate

interface Transacter {
  fun <T> transaction(lambda: (session: Session) -> T): T
}