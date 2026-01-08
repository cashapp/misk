package misk.web.metadata.guice

import com.google.inject.Inject
import java.net.URLEncoder

/** @GuiceSourceUrlProvider implementation that provides GitHub query URLs */
open class GitHubSourceUrlProvider @Inject constructor() : GuiceSourceUrlProvider {
  private val sourceWithLineNumberRegex = Regex("""^([\w.]+)\.((?:\w+\$?)+)\.(\w+)\(.*:(\d+)\)$""")
  private val classRegex = Regex("""class\s+([a-zA-Z0-9_.$]+)""")
  private val functionRegex =
    Regex("""(?:\w+\s+)?(?:\w+\s+)?(?:\w+\s+)?([a-zA-Z0-9_.$]+)\.([a-zA-Z0-9_.$]+)\(([^)]*)\)""")

  protected data class SourceLocation(
    val packageName: String,
    val className: String,
    val innerClassName: String?,
    val functionName: String?,
    val lineNumber: Int?,
  )

  override fun urlForSource(source: String): String? {
    val sourceLocation = maybeSourceLocation(source) ?: return null
    return githubSearchUrl(sourceLocation)
  }

  protected open fun generateQuery(source: SourceLocation): String {
    val sb = StringBuilder()
    sb.append(""""package ${source.packageName}"""")
    sb.append(" ${source.className}")
    if (source.innerClassName != null) {
      sb.append(" ${source.innerClassName}")
    }
    if (source.functionName != null) {
      sb.append(" ${source.functionName}")
    }
    return sb.toString()
  }

  private fun githubSearchUrl(source: SourceLocation): String {
    val query = generateQuery(source)
    return "https://github.com/search?q=${URLEncoder.encode(query, "UTF-8")}&type=code"
  }

  private fun maybeSourceLocation(source: String): SourceLocation? {
    sourceWithLineNumberRegex.matchEntire(source)?.let { matchResult ->
      val (packageName, className, functionName, lineNumberStr) = matchResult.destructured
      val (innerClassName, outerClassName) = parseClass(className)
      val lineNumber = lineNumberStr.toInt()
      return SourceLocation(
        packageName = packageName,
        className = outerClassName,
        innerClassName = innerClassName,
        functionName = functionName,
        lineNumber = lineNumber,
      )
    }

    classRegex.find(source)?.let { matchResult ->
      val fullClassName = matchResult.groupValues[1]
      val packageName = fullClassName.substringBeforeLast('.')
      val className = fullClassName.substringAfterLast('.')
      val (innerClassName, outerClassName) = parseClass(className)
      return SourceLocation(
        packageName = packageName,
        className = outerClassName,
        innerClassName = innerClassName,
        functionName = null,
        lineNumber = null,
      )
    }

    functionRegex.find(source)?.let { matchResult ->
      val fullClassName = matchResult.groupValues[1]
      val functionName = matchResult.groupValues[2].substringBefore('$')
      val packageName = fullClassName.substringBeforeLast('.')
      val className = fullClassName.substringAfterLast('.')
      val (innerClassName, outerClassName) = parseClass(className)
      return SourceLocation(
        packageName = packageName,
        className = outerClassName,
        innerClassName = innerClassName,
        functionName = functionName,
        lineNumber = null,
      )
    }

    return null
  }

  private fun parseClass(className: String): Pair<String?, String> {
    val innerClassName = if ('$' in className) className.substringAfter('$') else null
    val outerClassName = if ('$' in className) className.substringBefore('$') else className
    return Pair(innerClassName, outerClassName)
  }
}
