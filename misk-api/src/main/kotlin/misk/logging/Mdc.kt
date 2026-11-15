package misk.logging

typealias MdcContextMap = Map<String, String>

interface Mdc {
  fun put(key: String, value: String?)

  fun get(key: String): String?

  fun clear()

  fun setContextMap(context: MdcContextMap)

  fun getCopyOfContextMap(): MdcContextMap?
}
