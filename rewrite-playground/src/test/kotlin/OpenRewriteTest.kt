import org.junit.jupiter.api.Test
import org.openrewrite.java.AddImport
import org.openrewrite.java.Assertions.java
import org.openrewrite.java.JavaParser
import org.openrewrite.test.RewriteTest
import org.openrewrite.test.RewriteTest.toRecipe
import java.util.function.Supplier

internal class OpenRewriteTest : RewriteTest {

  @Test fun dontDuplicateImports3() {
    rewriteRun(
      { spec ->
        spec.recipe(toRecipe(
          Supplier {
            AddImport(
              "org.junit.jupiter.api.Assertions",
              "assertNull",
              false
            )
          }
        ))
          .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api"))
          .cycles(1).expectedCyclesThatMakeChanges(1)
      },
      java(
        """
              import static org.junit.jupiter.api.Assertions.assertFalse;
              import static org.junit.jupiter.api.Assertions.assertTrue;
                            
              import java.util.List;

              class A {}
              
              """.trimIndent(),
        """
              import static org.junit.jupiter.api.Assertions.*;
                            
              import java.util.List;
                            
              class A {}
              
              """.trimIndent()
      )
    )
  }
}
