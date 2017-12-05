package misk.resources

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResourceLoaderTest {
    @Test
    fun loadResource() {
        val resource = ResourceLoader.utf8("misk/resources/ResourceLoaderTest.txt")!!
        assertThat(resource).isEqualTo("69e0753934d2838d1953602ca7722444\n")
    }

    @Test
    fun absentResource() {
        assertThat(ResourceLoader.utf8("misk/resources/NoSuchResource.txt")).isNull()
    }
}
