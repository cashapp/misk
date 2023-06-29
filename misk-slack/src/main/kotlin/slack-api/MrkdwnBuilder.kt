package `slack-api`

/**
 * Mrkdwn is Slack's weird thing that isn't Markdown. This class attempts to implement proper
 * escaping and formatting.
 *
 * Note that the Mrkdwn docs aren't particularly helpful here.
 *
 * Note that this implementation is more conservative about encoding than strictly necessary. For
 * example, it escapes things like the colon at the end of `Notice:`, even though colons only really
 * need to be escaped when they signal an emoji (`:smile:`).
 *
 * https://api.slack.com/reference/surfaces/formatting#basics
 */
class MrkdwnBuilder {
  private val delegate = StringBuilder()
  fun append(s: String) {
    for (codePoint in s.codePoints()) {
      when (codePoint) {
        '`'.code -> delegate.append("'")
        '&'.code -> delegate.append("&amp;")
        '<'.code -> delegate.append("&lt;")
        '>'.code -> delegate.append("&gt;")

        '#'.code, '@'.code, ':'.code -> {
          delegate.appendCodePoint(codePoint)
          delegate.append(zwsp)
        }

        '*'.code, '_'.code, '~'.code -> {
          delegate.append("$zwsp`")
          delegate.appendCodePoint(codePoint)
          delegate.append("`$zwsp")
        }

        else -> {
          delegate.appendCodePoint(codePoint)
        }
      }
    }
  }

  fun appendLink(url: String, label: String) {
    require("|" !in url)
    require(">" !in url)

    delegate.append("<")
    delegate.append(url)
    delegate.append("|")
    append(label)
    delegate.append(">")

  }

  fun appendBlockquotePrefix() {
    delegate.append("> ")
  }

  fun build(): String {
    return delegate.toString()
  }
}

fun buildMrkdwn(builderAction: MrkdwnBuilder.() -> Unit): String {
  val builder = MrkdwnBuilder()
  builder.builderAction()
  return builder.build()
}

/**
 * Inserting a zero-width space is enough to prevent Slack from attempting to link a string like
 * :smile: as an emoji.
 *
 * https://en.wikipedia.org/wiki/Zero-width_space
 */
val zwsp = "\u200B"
