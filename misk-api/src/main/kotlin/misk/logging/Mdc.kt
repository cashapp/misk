package misk.logging

interface Mdc {
  fun put(key: String, value: String?)

  fun get(key: String): String?
}

