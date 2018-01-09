package misk.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections.shuffle

internal class PathPatternTest {
  @Test
  fun withOnlyConstants() {
    val p = PathPattern.parse("/a/b/c/d/e/f/g")
    assertThat(p.pattern.toString()).isEqualTo("\\Q/a/b/c/d/e/f/g\\E")
    assertThat(p.numSegments).isEqualTo(7)
    assertThat(p.numRegexVariables).isEqualTo(0)
    assertThat(p.matchesWildcardPath).isFalse()
    assertThat(p.variableNames).isEmpty()
  }

  @Test
  fun withSimpleVariableMatches() {
    val p = PathPattern.parse("/abc/{jeff}/def/{jesse}")
    assertThat(p.pattern.toString()).isEqualTo("\\Q/abc/\\E([^/]*)\\Q/def/\\E([^/]*)")
    assertThat(p.numSegments).isEqualTo(4)
    assertThat(p.numRegexVariables).isEqualTo(0)
    assertThat(p.matchesWildcardPath).isFalse()
    assertThat(p.variableNames).containsExactly("jeff", "jesse")

    val m1 = p.pattern.matcher("/abc/moo/def/bark")
    assertThat(m1.matches()).isTrue()
    assertThat(m1.group(1)).isEqualTo("moo")
    assertThat(m1.group(2)).isEqualTo("bark")

    assertThat(p.pattern.matcher("/abc/moo/def").matches()).isFalse()
    assertThat(p.pattern.matcher("/abc/moo/def/bark/more").matches()).isFalse()
  }

  @Test
  fun withRegexMatches() {
    val p = PathPattern.parse("/org/admin/{folder:[a-z]+}/{object:[a-z0-9]+}")
    assertThat(p.pattern.toString()).isEqualTo("\\Q/org/admin/\\E([a-z]+)\\Q/\\E([a-z0-9]+)")
    assertThat(p.numSegments).isEqualTo(4)
    assertThat(p.numRegexVariables).isEqualTo(2)
    assertThat(p.matchesWildcardPath).isFalse()
    assertThat(p.variableNames).containsExactly("folder", "object")

    val m1 = p.pattern.matcher("/org/admin/oranges/foo")
    assertThat(m1.matches()).isTrue()
    assertThat(m1.group(1)).isEqualTo("oranges")
    assertThat(m1.group(2)).isEqualTo("foo")

    val m2 = p.pattern.matcher("/org/admin/apples/bar75")
    assertThat(m2.matches()).isTrue()
    assertThat(m2.group(1)).isEqualTo("apples")
    assertThat(m2.group(2)).isEqualTo("bar75")

    assertThat(p.pattern.matcher("/org/admin/239034/bar75").matches()).isFalse()
    assertThat(p.pattern.matcher("/org/admin/apples/_admin").matches()).isFalse()
    assertThat(p.pattern.matcher("/org/admin/apples/_admin").matches()).isFalse()
    assertThat(p.pattern.matcher("/org/admin/apples/bar75/zod").matches()).isFalse()
  }

  @Test
  fun withFullPathCapture() {
    val p = PathPattern.parse("/org/admin/{object_type:o.*}/{path:.*}")
    assertThat(p.pattern.toString()).isEqualTo("\\Q/org/admin/\\E(o[^/]*)\\Q/\\E(.*)")
    assertThat(p.numSegments).isEqualTo(4)
    assertThat(p.numRegexVariables).isEqualTo(2)
    assertThat(p.matchesWildcardPath).isTrue()
    assertThat(p.variableNames).containsExactly("object_type", "path")

    val m1 = p.pattern.matcher("/org/admin/oranges/foo")
    assertThat(m1.matches()).isTrue()
    assertThat(m1.group(1)).isEqualTo("oranges")
    assertThat(m1.group(2)).isEqualTo("foo")

    val m2 = p.pattern.matcher("/org/admin/oranges/foo/bar")
    assertThat(m2.matches()).isTrue()
    assertThat(m2.group(1)).isEqualTo("oranges")
    assertThat(m2.group(2)).isEqualTo("foo/bar")

    assertThat(p.pattern.matcher("/org/admin/users/foo").matches()).isFalse()
  }

  @Test
  fun withWildcardCaptureInMiddle() {
    val p = PathPattern.parse("/org/admin/{object_type:.*}/get")
    assertThat(p.pattern.toString()).isEqualTo("\\Q/org/admin/\\E([^/]*)\\Q/get\\E")
    assertThat(p.numSegments).isEqualTo(4)
    assertThat(p.numRegexVariables).isEqualTo(1)
    assertThat(p.matchesWildcardPath).isFalse()
    assertThat(p.variableNames).containsExactly("object_type")

    assertThat(p.pattern.matcher("/org/admin/users/get").matches()).isTrue()
    assertThat(p.pattern.matcher("/org/admin/get").matches()).isFalse()
    assertThat(p.pattern.matcher("/org/admin/users/floozers").matches()).isFalse()
  }

  @Test
  fun ordering() {
    val p1 = PathPattern.parse("/org/{folder}/{type}")
    val p2 = PathPattern.parse("/org/admin/{type}")
    val p3 = PathPattern.parse("/org/admin/users")
    val p4 = PathPattern.parse("/org/admin/{path:.*}")
    val p5 = PathPattern.parse("/org/admin/{type:.*}/values")

    val patterns = mutableListOf(p1, p2, p3, p4, p5)
    shuffle(patterns)

    val sortedBySpecificity = patterns.sorted()
    assertThat(sortedBySpecificity).containsExactly(p5, p3, p2, p1, p4)
  }
}


