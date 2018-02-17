package misk.web

import java.util.regex.Pattern

/**
 * A path with placeholders for variables. Paths may be constant like `/app/home/` or dynamic like
 * `/user/{username}`. Variables are delimited by curly braces and may specify an optional regex
 * like this: `{username:[a-z]+}`. If no regex is specified the variable is a sequence of non-'/'
 * characters.
 */
data class PathPattern(
    val pattern: Pattern,
    val variableNames: List<String>,
    val numRegexVariables: Int,
    val numSegments: Int,
    val matchesWildcardPath: Boolean
) : Comparable<PathPattern> {

  /** Compares path patterns by specificity, with the more specific pattern ordered first */
  override fun compareTo(other: PathPattern): Int {
    // A path with more segments requires a more specific match
    val numSegmentDiff = other.numSegments - numSegments
    if (numSegmentDiff != 0) return numSegmentDiff

    // If we have the same number of fixed segments, but one of the patterns captures
    // the trailing part of the path in a variable, that pattern is less specific
    if (matchesWildcardPath && !other.matchesWildcardPath) return 1
    if (!matchesWildcardPath && other.matchesWildcardPath) return -1

    // Assuming we match on the same number of segments, then the pattern that has
    // more of those segments as constant text is a more specific match
    val numVariablesDiff = variableNames.size - other.variableNames.size
    if (numVariablesDiff != 0) return numVariablesDiff

    // Finally, the pattern which qualifies more of its variables with regexes is a
    // more specific match
    return other.numRegexVariables - numRegexVariables
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
              "invalid path pattern $pattern; variables must end on path boundaries"
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

            /*
            var nextMetacharacterStart = pattern.indexOfAny(charArrayOf('\\', '{'), pos)
            if (nextMetacharacterStart == -1) {
                nextMetacharacterStart = pattern.length
            }
            result.append("\\Q")
            result.append(pattern, pos, nextMetacharacterStart)
            result.append("\\E")
            pos = nextMetacharacterStart
            */
          }
        }
      }

      return PathPattern(
          Pattern.compile(result.toString()), variableNames, numRegexVariables,
          numSegments, lastVariableIsWildcardMatch
      )
    }
  }
}
