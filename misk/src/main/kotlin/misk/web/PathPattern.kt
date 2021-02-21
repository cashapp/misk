package misk.web

import okhttp3.HttpUrl
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * A path with placeholders for variables. Paths may be constant like `/app/home/` or dynamic like
 * `/user/{username}`. Variables are delimited by curly braces and may specify an optional regex
 * like this: `{username:[a-z]+}`. If no regex is specified the variable is a sequence of non-'/'
 * characters.
 */
class PathPattern(
  val pattern: String,
  val regex: Pattern,
  val variableNames: List<String>,
  val numRegexVariables: Int,
  val numSegments: Int,
  val matchesWildcardPath: Boolean
) : Comparable<PathPattern> {

  /** Returns a Matcher if requestUrl can be matched, else null */
  fun matcher(requestUrl: HttpUrl): Matcher? {
    val matcher = regex.matcher(requestUrl.encodedPath)
    return if (matcher.matches()) matcher else null
  }

  override fun hashCode(): Int = pattern.hashCode()

  override fun equals(other: Any?): Boolean = other is PathPattern && other.pattern == pattern

  override fun toString() = pattern

  /** Compares path patterns by specificity with the more specific pattern ordered first. */
  override fun compareTo(other: PathPattern): Int {
    // More segments comes first.
    val numSegmentsDiff = -numSegments.compareTo(other.numSegments)
    if (numSegmentsDiff != 0) return numSegmentsDiff

    // Not matching a wildcard comes first.
    val matchesWildcardPathDiff = matchesWildcardPath.compareTo(other.matchesWildcardPath)
    if (matchesWildcardPathDiff != 0) return matchesWildcardPathDiff

    // Fewer variables comes first.
    val numVariablesDiff = variableNames.size.compareTo(other.variableNames.size)
    if (numVariablesDiff != 0) return numVariablesDiff

    // More regexes comes first.
    val numRegexVariablesDiff = -numRegexVariables.compareTo(other.numRegexVariables)
    if (numRegexVariablesDiff != 0) return numRegexVariablesDiff

    return 0
  }

  companion object {
    fun parse(pattern: String): PathPattern {
      val variableNames = ArrayList<String>()
      val result = StringBuilder()

      var numSegments = 0
      var numRegexVariables = 0
      var lastVariableIsWildcardMatch = false
      var pos = 0
      while (pos < pattern.length) {
        lastVariableIsWildcardMatch = false

        when (pattern[pos]) {
          '{' -> {
            // Variables must start on path boundaries
            require(pos == 0 || pattern[pos - 1] == '/') {
              "invalid path pattern $pattern; variables must start on path boundaries"
            }

            val variableNameEnd = pattern.indexOfAny(charArrayOf('}', ':'), pos + 1)
            require(variableNameEnd != -1)
            variableNames.add(pattern.substring(pos + 1, variableNameEnd))
            if (pattern[variableNameEnd] == ':') {
              pos = pattern.indexOf('}', variableNameEnd + 1) + 1
              require(pos != -1)

              var regex = pattern.substring(variableNameEnd + 1, pos - 1)
              if (pos < pattern.length && regex.endsWith(".*")) {
                regex = regex.substring(0, regex.length - 2) + "[^/]*"
              }

              result.append("(")
                .append(regex)
                .append(")")
              numRegexVariables++

              lastVariableIsWildcardMatch = regex.endsWith(".*")
            } else {
              pos = variableNameEnd + 1
              result.append("([^/]*)")
            }

            // Variables must end on path boundaries
            require(pos == pattern.length || pattern[pos] == '/') {
              "invalid path regex $pattern; variables must end on path boundaries"
            }
          }

          '\\' -> {
            result.append("\\\\")
            pos++
          }

          else -> {
            result.append("\\Q")
            while (pos < pattern.length && pattern[pos] != '\\' && pattern[pos] != '{') {
              if (pattern[pos] == '/') numSegments++

              result.append(pattern[pos])
              pos++
            }
            result.append("\\E")
          }
        }
      }

      return PathPattern(
        pattern,
        Pattern.compile(result.toString()),
        variableNames,
        numRegexVariables,
        numSegments,
        lastVariableIsWildcardMatch
      )
    }
  }
}
