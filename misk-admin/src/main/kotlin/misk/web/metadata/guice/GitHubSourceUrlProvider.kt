package misk.web.metadata.guice

import com.google.inject.Inject
import java.net.URLEncoder

/**
 * @GuiceSourceUrlProvider implementation that provides GitHub query URLs
 */

open class GitHubSourceUrlProvider @Inject constructor() : GuiceSourceUrlProvider {
  private val sourceWithLineNumberRegex = """^([\w.]+)\.((?:\w+\$?)+)\.(\w+)\(.*:(\d+)\)$""".toRegex()
  protected data class SourceLocation(
    val packageName: String,
    val className: String,
    val functionName: String,
    val lineNumber: Int,
  )
  override fun urlForSource(source: String): String? {
    val sourceLocation = maybeSourceLocation(source) ?: return null
    return githubSearchUrl(sourceLocation)
  }

  protected open fun generateQuery(source: SourceLocation): String {
    return """"package ${source.packageName}" ${source.className.replace('$', ' ')} ${source.functionName}"""
  }

  private fun githubSearchUrl(source: SourceLocation): String {
    val query = generateQuery(source)
    return "https://github.com/search?q=${URLEncoder.encode(query, "UTF-8")}&type=code"
  }

  private fun maybeSourceLocation(source: String): SourceLocation? {
    val matchResult = sourceWithLineNumberRegex.matchEntire(source)

    return if (matchResult != null) {
      // Extract the package, class, function names, and line number from the match groups
      val (packageName, className, functionName, lineNumberStr) = matchResult.destructured
      val lineNumber = lineNumberStr.toInt()
      SourceLocation(packageName, className, functionName, lineNumber)
    } else {
      null
    }
  }
}
