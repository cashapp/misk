package misk.testing

import com.google.inject.BindingAnnotation
import com.google.inject.Provides
import misk.inject.KAbstractModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
internal class InjectingParameterResolverTest {
  @MiskTestModule val module = object : KAbstractModule() {
    @Provides fun myString(): String = "a string"

    @Provides @TestAnnotation fun myAnnotatedList(): List<String> = listOf("strings?")
  }

  @BindingAnnotation
  annotation class TestAnnotation

  @Test @Inject fun `retrieves all parameters if the method is annotated`(
    myString: String,
    @TestAnnotation myList: List<String>
  ) {
    assertThat(myString).isEqualTo("a string")

    assertThat(myList).containsExactly("strings?")
  }
}
