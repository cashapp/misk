package misk.mcp.internal

import java.io.ByteArrayOutputStream
import java.net.URI

/**
 * Matches resource URIs against an RFC 6570 URI template.
 *
 * Supports simple `{var}` expressions, which match up to the next `/`, and reserved `{+var}` expressions, which match
 * any characters including `/`. Other operators and multi-variable expressions are treated as literal text. Captured
 * values are percent-decoded once.
 *
 * Captured values come from the client: handlers must validate them before using them in paths, queries or URLs.
 */
internal class UriTemplate(val template: String) {
  private val variableNames = mutableListOf<String>()
  private val reservedCount: Int
  private val literalLength: Int
  private val regex: Regex

  init {
    require(template.isNotBlank()) { "Resource template cannot be blank" }
    val pattern = StringBuilder()
    var literals = 0
    var reserved = 0
    var literalStart = 0
    fun appendLiteral(end: Int) {
      val literal = template.substring(literalStart, end)
      pattern.append(Regex.escape(literal))
      literals += literal.length
    }
    for (expression in EXPRESSION.findAll(template)) {
      val variable = parseExpression(expression.groupValues[1]) ?: continue
      appendLiteral(expression.range.first)
      variableNames += variable.name
      if (variable.reserved) reserved++
      pattern.append(if (variable.reserved) "(.+)" else "([^/]+)")
      literalStart = expression.range.last + 1
    }
    appendLiteral(template.length)
    literalLength = literals
    reservedCount = reserved
    regex = Regex(pattern.toString())
  }

  fun match(uri: String): Match? {
    if (uri.length > MAX_URI_LENGTH) return null
    val result = regex.matchEntire(normalize(uri)) ?: return null
    val variables =
      variableNames.zip(result.groupValues.drop(1)).associate { (name, value) -> name to percentDecode(value) }
    return Match(variables, literalLength, reservedCount)
  }

  class Match(val variables: Map<String, String>, private val literalLength: Int, private val reservedCount: Int) :
    Comparable<Match> {
    /** More literal text is more specific; then fewer reserved variables; then fewer variables. */
    override fun compareTo(other: Match): Int =
      compareValuesBy(this, other, { it.literalLength }, { -it.reservedCount }, { -it.variables.size })
  }

  private class Variable(val name: String, val reserved: Boolean)

  private fun parseExpression(expression: String): Variable? {
    val reserved = expression.startsWith("+")
    val name = expression.removePrefix("+")
    if (name.isEmpty() || !name.all { it.isLetterOrDigit() || it == '_' || it == '.' }) return null
    return Variable(name, reserved)
  }

  private fun normalize(uri: String): String =
    try {
      URI(uri).normalize().toString()
    } catch (_: Exception) {
      uri
    }

  private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < value.length) {
      val c = value[i]
      if (c == '%' && i + 2 < value.length) {
        val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
        if (hex != null) {
          bytes.write(hex)
          i += 3
          continue
        }
      }
      bytes.write(c.toString().toByteArray(Charsets.UTF_8))
      i++
    }
    return bytes.toString(Charsets.UTF_8)
  }

  companion object {
    private const val MAX_URI_LENGTH = 2048
    private val EXPRESSION = Regex("\\{([^{}]*)}")
  }
}
