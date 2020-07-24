package misk.security.cert

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class X500NameTest {
  @Test fun parse() {
    val name = X500Name.parse(
        "CN=Marshall T. Rose, O=Dover Beach Consulting Ltd., L=Santa Clara, ST=California, OU=Sales, C=US\n")
    assertThat(name).isEqualTo(X500Name(
        "Marshall T. Rose",
        "Sales",
        "Dover Beach Consulting Ltd.",
        "Santa Clara",
        "California",
        "US"
    ))
  }

  @Test fun parseWithEscaping() {
    val name = X500Name.parse(
        """CN=Marshall T. Rose\, Esq., O="Dover Beach Consulting, Ltd."; L = Santa Clara; OU=Sales, ST=California, C=US""")
    assertThat(name).isEqualTo(X500Name(
        "Marshall T. Rose, Esq.",
        "Sales",
        "Dover Beach Consulting, Ltd.",
        "Santa Clara",
        "California",
        "US"
    ))
  }

  @Test fun handlesTrailingWhitespace() {
    val name = X500Name.parse("CN=Marshall T. Rose\n  \t")
    assertThat(name).isEqualTo(X500Name(mapOf("CN" to "Marshall T. Rose")))
  }

  @Test fun endsInAttributeName() {
    val e = assertFailsWith<IllegalArgumentException> {
      X500Name.parse("CN")
    }

    assertThat(e).hasMessage("invalid X.500 name 'CN'; unfinished attribute CN")
  }

  @Test fun noAttributes() {
    val e = assertFailsWith<IllegalArgumentException> {
      X500Name.parse("    \n")
    }

    assertThat(e).hasMessage("invalid X.500 name '    \n'; no attributes")
  }

  @Test fun blankAttributeName() {
    val e = assertFailsWith<IllegalArgumentException> {
      X500Name.parse("=Marshall T. Rose")
    }

    assertThat(e).hasMessage("invalid X.500 name '=Marshall T. Rose'; attribute name is blank")
  }

  @Test fun nakedAttributeName() {
    val e = assertFailsWith<IllegalArgumentException> {
      X500Name.parse("CN,")
    }

    assertThat(e).hasMessage("invalid X.500 name 'CN,'; no attribute value for CN")
  }

  @Test fun unescapedEqualsInAttributeValue() {
    val e = assertFailsWith<IllegalArgumentException> {
      X500Name.parse("CN=Marshall = ")
    }

    assertThat(e).hasMessage(
            "invalid X.500 name 'CN=Marshall = '; illegal character '=' in attribute value CN")
  }
}
