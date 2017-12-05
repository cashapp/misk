package misk.web

import java.util.regex.Pattern

/**
 * A path with placeholders for variables. Paths may be constant like `/app/home/` or dynamic like
 * `/user/{username}`. Variables are delimited by curly braces and may specify an optional regex
 * like this: `{username:[a-z]+}`. If no regex is specified the variable is a sequence of non-'/'
 * characters.
 */
data class PathPattern(val pattern: Pattern, val variableNames: List<String>) {
  companion object {
    fun parse(pattern: String): PathPattern {
      val variableNames = ArrayList<String>()
      val result = StringBuilder()

      var pos = 0
      while (pos < pattern.length) {
        when (pattern[pos]) {
          '{' -> {
            val variableNameEnd = pattern.indexOfAny(charArrayOf('}', ':'), pos + 1)
            require(variableNameEnd != -1)
            variableNames.add(pattern.substring(pos + 1, variableNameEnd))
            if (pattern[variableNameEnd] == ':') {
              pos = pattern.indexOf('}', variableNameEnd + 1) + 1
              require(pos != -1)
              result.append("(").append(pattern, variableNameEnd + 1, pos - 1).append(")")
            } else {
              pos = variableNameEnd + 1
              result.append("([^/]*)")
            }
          }

          '\\' -> {
            result.append("\\\\")
            pos++
          }

          else -> {
            var nextMetacharacterStart = pattern.indexOfAny(charArrayOf('\\', '{'), pos)
            if (nextMetacharacterStart == -1) {
              nextMetacharacterStart = pattern.length
            }
            result.append("\\Q")
            result.append(pattern, pos, nextMetacharacterStart)
            result.append("\\E")
            pos = nextMetacharacterStart
          }
        }
      }

      return PathPattern(Pattern.compile(result.toString()), variableNames)
    }
  }
}
