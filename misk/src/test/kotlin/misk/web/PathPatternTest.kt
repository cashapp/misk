package misk.web

import com.google.common.truth.Truth.assertThat
import org.junit.Test


internal class PathPatternTest {
    @Test
    fun parse() {
        assertThat(PathPattern.parse("/abc/{jeff}/def").pattern.toString())
                .isEqualTo("\\Q/abc/\\E([^/]*)\\Q/def\\E")
        assertThat(PathPattern.parse("\\Q").pattern.toString())
                .isEqualTo("\\\\\\QQ\\E")
        assertThat(PathPattern.parse("/{name:regex}").pattern.toString())
                .isEqualTo("\\Q/\\E(regex)")
    }

    @Test
    fun variableNames() {
        val pathPattern = PathPattern.parse("/abc/{jeff}/def/{jesse}")
        assertThat(pathPattern.variableNames).containsExactly("jeff", "jesse")
    }
}


