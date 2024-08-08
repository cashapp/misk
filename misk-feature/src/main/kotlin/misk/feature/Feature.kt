package misk.feature

class Feature(name: String) : wisp.feature.Feature(name)

class Attributes @JvmOverloads constructor(
  text: Map<String, String> = mapOf(),
  // NB: LaunchDarkly uses typed Gson attributes. We could leak that through, but that could make
  // code unwieldly. Numerical attributes are likely to be rarely used, so we make it a separate,
  // optional field rather than trying to account for multiple possible attribute types.
  number: Map<String, Number>? = null,
  // Indicates that the user is anonymous, which may have backend-specific behavior, like not
  // including the user in analytics.
  anonymous: Boolean = false
) : wisp.feature.Attributes(text, number, anonymous) {
  override fun with(name: String, value: String): Attributes =
    copy(text = text.plus(name to value))
  
  override fun with(name: String, value: Number): Attributes {
    val number = number ?: mapOf()
    return copy(number = number.plus(name to value))
  }

  override fun copy(
    text: Map<String, String>,
    number: Map<String, Number>?,
    anonymous: Boolean
  ): Attributes = Attributes(text, number, anonymous)
}
