package misk.security.cert

data class X500Name(
  val commonName: String?,
  val organizationalUnit: String?,
  val organization: String?,
  val locality: String?,
  val state: String?,
  val country: String?
) {
  constructor(components: Map<String, String>) : this(
      commonName = components.get("CN"),
      organizationalUnit = components.get("OU"),
      organization = components.get("O"),
      locality = components.get("L"),
      state = components.get("ST"),
      country = components.get("C")
  )

  companion object {
    fun parse(dnString: String): X500Name {
      val components = mutableMapOf<String, String>()

      val dn = dnString.toCharArray()

      var index = 0
      var escaped = false
      var quoteEscaped = false
      var openNameOrValue = StringBuilder()
      var inAttributeName = true
      var attributeName: String? = null

      while (index < dn.size) {
        val c = dn[index]

        when {
        // Current character is being escaped, so should be added as-is to the open name or value
          escaped -> {
            openNameOrValue.append(c)
            escaped = false
          }

        // Either starts or ends a quoted string
          c == '"' -> quoteEscaped = !quoteEscaped

        // Currently in a quoted string, so add the character as-is to the open name or value
          quoteEscaped -> openNameOrValue.append(c)

        // Next character will be escaped
          c == '\\' -> escaped = true

        // Ends the attribute entirely. An error if we never encountered a value, otherwise
        // trim the current value and add to the component map
          c == ',' || c == ';' -> {
            require(!inAttributeName) {
              "invalid X.500 name '$dnString'; no attribute value for $openNameOrValue"
            }

            components[attributeName!!] = openNameOrValue.toString().trim()

            attributeName = null
            openNameOrValue = StringBuilder()
            inAttributeName = true
          }

        // Ends the attribute name. An error if we are parsing an attribute value, otherwise
        // trim and save off the attribute name, and begin parsing the attribute value
          c == '=' -> {
            require(inAttributeName) {
              "invalid X.500 name '$dnString'; illegal character '=' in attribute value $attributeName"
            }
            inAttributeName = false
            attributeName = openNameOrValue.toString().trim()
            require(attributeName.isNotBlank()) {
              "invalid X.500 name '$dnString'; attribute name is blank"
            }
            openNameOrValue = StringBuilder()
          }

        // Not a special character, add directly to the open name or value
          else -> openNameOrValue.append(c)
        }

        index++
      }

      // We need to end with either having completed an attribute name + value, or without
      // having started an attribute
      require(openNameOrValue.isBlank() || !inAttributeName) {
        "invalid X.500 name '$dnString'; unfinished attribute $openNameOrValue"
      }

      if (!inAttributeName) {
        components[attributeName!!] = openNameOrValue.toString().trim()
      }

      require(!components.isEmpty()) { "invalid X.500 name '$dnString'; no attributes" }

      return X500Name(components.toMap())
    }
  }
}
